"""점주 웹 화면 (PoC 범위: 로그인 / 로그아웃 / 대시보드).

ORM 호출이 한 줄도 없다는 점이 이 파일의 핵심이다. 모든 데이터는
api_client 를 통해 /api/v1 에서 가져온다.
"""

import logging
from datetime import date, datetime

from django.contrib import messages
from django.shortcuts import redirect, render

from .api_client import ApiError, client_for
from .auth import login_session, logout_session, owner_required

logger = logging.getLogger(__name__)

# 매출로 집계할 주문 상태. apps/owner/sales.py 의 _SALES_STATUSES 와 맞춰야 한다.
SALES_STATUSES = {'placed', 'accepted', 'cooking', 'cooked', 'delivering', 'delivered'}
COOKING_STATUSES = {'accepted', 'cooking', 'cooked'}

ORDER_STATUS_LABELS = {
    'placed': '접수 대기',
    'accepted': '접수됨',
    'cooking': '조리중',
    'cooked': '조리완료',
    'delivering': '배달중',
    'delivered': '배달완료',
    'rejected': '거절됨',
    'cancelled': '취소됨',
}


def _fmt_dt(iso_string):
    """API 가 주는 ISO8601 문자열을 'm/d H:i' 로. ORM 직결일 때는 date 필터가
    datetime 객체를 알아서 포맷했지만, 이제는 문자열이라 직접 파싱해야 한다."""
    if not iso_string:
        return ''
    try:
        return datetime.fromisoformat(iso_string).strftime('%m/%d %H:%M')
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
