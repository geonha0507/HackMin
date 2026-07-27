"""점주 웹 화면 (PoC 범위: 로그인 / 로그아웃 / 대시보드).

ORM 호출이 한 줄도 없다는 점이 이 파일의 핵심이다. 모든 데이터는
api_client 를 통해 /api/v1 에서 가져온다.
"""

import logging
from datetime import date, datetime, timedelta

from django.contrib import messages
from django.http import Http404, HttpResponse
from django.shortcuts import redirect, render

from .api_client import ApiError, client_for
from .auth import login_session, logout_session, owner_required

logger = logging.getLogger(__name__)

# 매출로 집계할 주문 상태. apps/owner/sales.py 의 _SALES_STATUSES 와 맞춰야 한다.
SALES_STATUSES = {'placed', 'accepted', 'cooking', 'cooked', 'delivering', 'delivered'}
COOKING_STATUSES = {'accepted', 'cooking', 'cooked'}

# apps/orders/models.py 의 Order.Status 와 동기화할 것.
# (API 응답의 status_display 를 우선 쓰지만, 필터 드롭다운에는 순서 있는 목록이 필요하다)
ORDER_STATUS_CHOICES = [
    ('pending', '결제대기'),
    ('placed', '점주확인대기'),
    ('accepted', '주문접수'),
    ('rejected', '주문거절'),
    ('cooking', '조리중'),
    ('cooked', '조리완료'),
    ('delivering', '배달중'),
    ('delivered', '배달완료'),
    ('cancelled', '주문취소'),
]
ORDER_STATUS_LABELS = dict(ORDER_STATUS_CHOICES)


def _fmt_dt(iso_string, fmt='%m/%d %H:%M'):
    """API 가 주는 ISO8601 문자열을 'm/d H:i' 로. ORM 직결일 때는 date 필터가
    datetime 객체를 알아서 포맷했지만, 이제는 문자열이라 직접 파싱해야 한다."""
    if not iso_string:
        return ''
    try:
        return datetime.fromisoformat(iso_string).strftime(fmt)
    except (TypeError, ValueError):
        return str(iso_string)[:16]


def _safe_next(raw):
    """오픈 리다이렉트 방지: 같은 사이트 경로만 허용한다."""
    if raw and raw.startswith('/') and not raw.startswith('//'):
        return raw
    return '/web/owner/'


# ------------------------------------------------------------------ 인증
def login_view(request):
    next_url = _safe_next(request.POST.get('next') or request.GET.get('next'))

    if request.web_user.is_authenticated and request.web_user.role == 'owner':
        return redirect(next_url)

    if request.method == 'POST':
        username = (request.POST.get('username') or '').strip()
        password = request.POST.get('password') or ''
        if not username or not password:
            messages.error(request, '아이디와 비밀번호를 입력해주세요.')
            return render(request, 'web/login.html', {'next': next_url, 'hide_chrome': True})

        try:
            payload = client_for(request).login(username, password)
        except ApiError as exc:
            messages.error(request, exc.message)
            return render(request, 'web/login.html', {'next': next_url, 'hide_chrome': True})

        role = (payload.get('user') or {}).get('role')
        if role != 'owner':
            # 이 컨테이너는 점주 전용이다. 다른 역할의 토큰은 세션에 남기지 않는다.
            messages.error(request, '점주 계정으로만 접속할 수 있습니다.')
            return render(request, 'web/login.html', {'next': next_url, 'hide_chrome': True})

        login_session(request, payload)
        return redirect(next_url)

    return render(request, 'web/login.html', {'next': next_url, 'hide_chrome': True})


def logout_view(request):
    if request.web_user.is_authenticated:
        logout_session(request)
    return redirect('/web/login')


# ------------------------------------------------------------------ 대시보드
@owner_required
def dashboard(request):
    api = client_for(request)
    today = date.today().isoformat()

    ctx = {
        'restaurants': [],
        'pending_count': 0,
        'cooking_count': 0,
        'today_order_count': 0,
        'today_sales': 0,
        'total_sales': 0,
        'recent_orders': [],
        'menu_count': 0,
        'api_degraded': False,
    }

    # 호출 하나가 실패해도 화면 전체가 죽지 않게 각각 감싼다.
    # (ORM 직결일 때는 없던 실패 모드다 — 3-tier 로 가면 반드시 다뤄야 한다)
    def _call(path, **kw):
        try:
            return api.get(path, **kw)
        except ApiError as exc:
            logger.warning('대시보드 %s 실패: %s', path, exc)
            ctx['api_degraded'] = True
            return None

    restaurants = _call('/owner/restaurants')
    if restaurants:
        ctx['restaurants'] = restaurants.get('results', [])

    stats = _call('/owner/sales/stats')
    if stats:
        by_status = stats.get('by_status', [])
        ctx['pending_count'] = sum(s['count'] for s in by_status if s['status'] == 'placed')
        ctx['cooking_count'] = sum(s['count'] for s in by_status if s['status'] in COOKING_STATUSES)
        ctx['total_sales'] = sum(
            (s.get('sales') or 0) for s in by_status if s['status'] in SALES_STATUSES
        )

    # 주의: /owner/sales 는 상태 필터가 없어 취소·거절 주문까지 합산한다.
    # 기존 apps/web 의 today_sales(= SALES_STATUSES 만 합산)와 값이 달라질 수 있다.
    # PoC 검증 항목이며, 정식 이행 시 API 에 status 필터를 추가해 맞춘다.
    today_sales = _call('/owner/sales', params={'start': today, 'end': today})
    if today_sales:
        ctx['today_order_count'] = today_sales.get('order_count', 0)
        ctx['today_sales'] = today_sales.get('total_sales', 0)

    orders = _call('/owner/orders')
    if orders:
        recent = orders.get('results', [])[:8]
        for o in recent:
            # 매장명은 OrderSerializer 의 restaurant_name 을 쓴다 (id 만으로는 표시 불가).
            o['status_label'] = o.get('status_display') or ORDER_STATUS_LABELS.get(
                o.get('status'), o.get('status')
            )
            o['created_display'] = _fmt_dt(o.get('created_at'))
        ctx['recent_orders'] = recent

    products = _call('/owner/products')
    if products:
        ctx['menu_count'] = len(products.get('results', []))

    if ctx['api_degraded']:
        messages.error(request, '일부 데이터를 불러오지 못했습니다. 표시된 수치가 정확하지 않을 수 있습니다.')

    return render(request, 'web/owner/dashboard.html', ctx)


# ------------------------------------------------------------------ 주문 관리
@owner_required
def order_list(request):
    api = client_for(request)
    status = request.GET.get('status', '')

    orders = []
    try:
        payload = api.get('/owner/orders', params={'status': status} if status else None)
        orders = payload.get('results', [])[:200]
    except ApiError as exc:
        messages.error(request, exc.message)

    for o in orders:
        o['created_display'] = _fmt_dt(o.get('created_at'))

    return render(request, 'web/owner/orders.html', {
        'orders': orders,
        'status': status,
        'status_choices': ORDER_STATUS_CHOICES,
    })


# 화면의 action 값 → 호출할 API 경로. 상태 전이 규칙(placed 에서만 accept 가능 등)은
# 서버가 판단한다. 예전 apps/web 은 여기서 직접 검사했지만, 이제 규칙이 한 곳에만
# 존재하므로 웹과 앱의 동작이 어긋날 수 없다.
_ORDER_ACTION_PATHS = {
    'accept': 'accept',
    'reject': 'reject',
    'cancel': 'cancel',
}


@owner_required
def order_detail(request, pk):
    api = client_for(request)

    if request.method == 'POST':
        action = request.POST.get('action')
        try:
            if action in _ORDER_ACTION_PATHS:
                api.post(f'/owner/orders/{pk}/{_ORDER_ACTION_PATHS[action]}')
            elif action == 'status':
                api.post(f'/owner/orders/{pk}/status',
                         json={'status': request.POST.get('status')})
            else:
                messages.error(request, '알 수 없는 요청입니다.')
                return redirect('web:owner_order_detail', pk=pk)
            messages.success(request, '주문 상태를 변경했습니다.')
        except ApiError as exc:
            messages.error(request, exc.message)
        return redirect('web:owner_order_detail', pk=pk)

    try:
        order = api.get(f'/owner/orders/{pk}')
    except ApiError as exc:
        if exc.status_code == 404:
            raise Http404('주문을 찾을 수 없습니다.')
        messages.error(request, exc.message)
        return redirect('web:owner_orders')

    order['created_display'] = _fmt_dt(order.get('created_at'), '%Y-%m-%d %H:%M')

    return render(request, 'web/owner/order_detail.html', {
        'order': order,
        'status_choices': ORDER_STATUS_CHOICES,
    })


# ------------------------------------------------------------------ 상품/카테고리
# apps/restaurants/models.py 의 Menu.Status 와 동기화할 것.
# 모델의 choices 라벨은 영어라 화면용 한글 라벨을 여기서 정의한다.
MENU_STATUS_CHOICES = [
    ('on_sale', '판매중'),
    ('sold_out', '품절'),
    ('hidden', '숨김'),
]
MAX_MENU_PRICE = 10_000_000


def _owned_restaurants(api, reviewable_only=False):
    params = {'reviewable': '1'} if reviewable_only else None
    return api.get('/owner/restaurants', params=params).get('results', [])


def _selected_restaurant_id(request, restaurants):
    """GET 파라미터 restaurant_id 로 필터. 소유하지 않은 ID 는 무시한다."""
    try:
        sel = int(request.GET.get('restaurant_id', ''))
    except (TypeError, ValueError):
        return ''
    return sel if any(r['id'] == sel for r in restaurants) else ''


@owner_required
def product_list(request):
    api = client_for(request)
    restaurants, menus = [], []
    try:
        restaurants = _owned_restaurants(api)
        menus = api.get('/owner/products').get('results', [])
    except ApiError as exc:
        messages.error(request, exc.message)

    selected = _selected_restaurant_id(request, restaurants)
    if selected:
        menus = [m for m in menus if m.get('restaurant') == selected]

    return render(request, 'web/owner/products.html', {
        'menus': menus,
        'restaurants': restaurants,
        'selected_restaurant_id': selected,
    })


@owner_required
def product_form(request, pk=None):
    api = client_for(request)

    if request.method == 'POST':
        try:
            price = int(request.POST.get('price') or 0)
        except (TypeError, ValueError):
            price = -1

        payload = {
            'restaurant': request.POST.get('restaurant'),
            'category': request.POST.get('category') or None,
            'name': (request.POST.get('name') or '').strip(),
            'price': price,
            'description': (request.POST.get('description') or '').strip(),
            'status': request.POST.get('status') or 'on_sale',
        }

        if price < 0:
            messages.error(request, '가격을 올바르게 입력하세요.')
        else:
            try:
                # 값 검증(상품명 공백, 가격 상한, 매장 승인 여부)은 서버가 한다.
                if pk:
                    saved = api.put(f'/owner/products/{pk}', json=payload)
                else:
                    saved = api.post('/owner/products', json=payload)

                upload = request.FILES.get('image')
                if upload:
                    # 이미지는 별도 multipart 엔드포인트로 중계한다.
                    # (상품 본문은 JSON, 파일은 multipart 로 나뉜 구조)
                    api.post(
                        f"/owner/products/{saved['id']}/image",
                        files={'image': (upload.name, upload.file, upload.content_type)},
                    )
                messages.success(request, '상품을 저장했습니다.')
                return redirect('web:owner_products')
            except ApiError as exc:
                messages.error(request, exc.message)

    menu, restaurants, categories = None, [], []
    try:
        # 등록 대상은 승인 완료된 매장만 노출한다.
        restaurants = _owned_restaurants(api, reviewable_only=True)
        categories = api.get('/owner/categories').get('results', [])
        if pk:
            menu = api.get(f'/owner/products/{pk}')
    except ApiError as exc:
        if pk and exc.status_code == 404:
            raise Http404('상품을 찾을 수 없습니다.')
        messages.error(request, exc.message)

    return render(request, 'web/owner/product_form.html', {
        'menu': menu,
        'restaurants': restaurants,
        'categories': categories,
        'status_choices': MENU_STATUS_CHOICES,
    })


@owner_required
def product_delete(request, pk):
    if request.method == 'POST':
        try:
            client_for(request).delete(f'/owner/products/{pk}')
            messages.success(request, '상품을 삭제했습니다.')
        except ApiError as exc:
            messages.error(request, exc.message)
    return redirect('web:owner_products')


@owner_required
def category_list(request):
    api = client_for(request)

    if request.method == 'POST':
        action = request.POST.get('action')
        category_id = request.POST.get('category_id')
        try:
            if action == 'create':
                api.post('/owner/categories', json={
                    'restaurant': request.POST.get('restaurant'),
                    'name': (request.POST.get('name') or '').strip(),
                })
                messages.success(request, '카테고리를 추가했습니다.')
            elif action == 'delete':
                api.delete(f'/owner/categories/{category_id}')
                messages.success(request, '카테고리를 삭제했습니다.')
            elif action in ('move_up', 'move_down'):
                # 순서 교환은 서버가 한 트랜잭션으로 처리한다.
                api.post(f'/owner/categories/{category_id}/move',
                         json={'direction': 'up' if action == 'move_up' else 'down'})
                messages.success(request, '순서를 변경했습니다.')
            else:
                messages.error(request, '알 수 없는 요청입니다.')
        except ApiError as exc:
            messages.error(request, exc.message)
        return redirect('web:owner_categories')

    categories, restaurants = [], []
    try:
        categories = api.get('/owner/categories').get('results', [])
        restaurants = _owned_restaurants(api, reviewable_only=True)
    except ApiError as exc:
        messages.error(request, exc.message)

    return render(request, 'web/owner/categories.html', {
        'categories': categories,
        'restaurants': restaurants,
    })


# ------------------------------------------------------------------ 매출 분석
@owner_required
def sales(request):
    api = client_for(request)
    today = date.today()
    start = request.GET.get('start') or str(today - timedelta(days=29))
    end = request.GET.get('end') or str(today)

    restaurants = []
    try:
        restaurants = _owned_restaurants(api)
    except ApiError as exc:
        messages.error(request, exc.message)

    selected = _selected_restaurant_id(request, restaurants)
    params = {'start': start, 'end': end}
    if selected:
        params['restaurant_id'] = selected

    daily, by_menu = [], []
    total_sales, order_count = 0, 0
    try:
        summary = api.get('/owner/sales', params=params)
        daily = summary.get('daily', [])
        total_sales = summary.get('total_sales', 0)
        order_count = summary.get('order_count', 0)
        by_menu = api.get('/owner/sales/by-menu', params=params).get('results', [])
    except ApiError as exc:
        messages.error(request, exc.message)

    # 막대 폭은 화면 표현이라 서버에서 계산하지 않고 여기서 만든다.
    max_amount = max([d.get('sales') or 0 for d in daily], default=0)
    for d in daily:
        d['pct'] = round((d.get('sales') or 0) / max_amount * 100) if max_amount else 0

    return render(request, 'web/owner/sales.html', {
        'start': start, 'end': end,
        'daily': daily,
        'by_menu': by_menu,
        'total_sales': total_sales,
        'order_count': order_count,
        'restaurants': restaurants,
        'selected_restaurant_id': selected,
    })


@owner_required
def sales_report_download(request, pk):
    """매출 보고서 다운로드 중계.

    예전에는 템플릿이 /api/v1/downloads/... 를 직접 링크했다. 같은 Django
    프로세스라 세션 인증이 통했기 때문이다. 이제 API 는 별도 컨테이너이고
    브라우저에는 JWT 가 없으므로 web_bff 가 대신 받아 넘긴다.
    """
    try:
        upstream = client_for(request).raw('GET', f'/downloads/sales-report/{pk}')
    except ApiError as exc:
        messages.error(request, exc.message)
        return redirect('web:owner_sales')

    response = HttpResponse(
        upstream.content,
        content_type=upstream.headers.get('content-type', 'application/octet-stream'),
    )
    disposition = upstream.headers.get('content-disposition')
    if disposition:
        response['Content-Disposition'] = disposition
    return response


# ------------------------------------------------------------------ 리뷰 관리
def _reviews_redirect(selected):
    url = '/web/owner/reviews'
    return redirect(f'{url}?restaurant_id={selected}' if selected else url)


@owner_required
def review_list(request):
    api = client_for(request)

    if request.method == 'POST':
        selected = request.POST.get('restaurant_id') or ''
        review_id = request.POST.get('review_id')
        try:
            api.post(f'/owner/reviews/{review_id}/reply',
                     json={'content': (request.POST.get('content') or '').strip()})
            messages.success(request, '답변을 등록했습니다.')
        except ApiError as exc:
            messages.error(request, exc.message)
        return _reviews_redirect(selected)

    restaurants, reviews = [], []
    try:
        restaurants = _owned_restaurants(api)
    except ApiError as exc:
        messages.error(request, exc.message)

    selected = _selected_restaurant_id(request, restaurants)
    try:
        params = {'restaurant_id': selected} if selected else None
        reviews = api.get('/owner/reviews', params=params).get('results', [])
    except ApiError as exc:
        messages.error(request, exc.message)

    for r in reviews:
        r['created_display'] = _fmt_dt(r.get('created_at'), '%Y-%m-%d')
        r['deleted_display'] = _fmt_dt(r.get('deleted_at'), '%Y-%m-%d %H:%M')
        if r.get('reply'):
            r['reply']['created_display'] = _fmt_dt(r['reply'].get('created_at'), '%Y-%m-%d')

    return render(request, 'web/owner/reviews.html', {
        'reviews': reviews,
        'restaurants': restaurants,
        'selected_restaurant_id': selected,
    })


@owner_required
def review_delete(request, pk):
    """점주의 리뷰 소프트 삭제(사유 필수)."""
    selected = request.GET.get('restaurant_id') or ''
    if request.method == 'POST':
        try:
            client_for(request).post(
                f'/owner/reviews/{pk}/delete',
                json={'reason': (request.POST.get('reason') or '').strip()},
            )
            messages.success(request, '리뷰를 삭제했습니다.')
        except ApiError as exc:
            messages.error(request, exc.message)
    return _reviews_redirect(selected)
