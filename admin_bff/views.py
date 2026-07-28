"""관리자 웹 화면. ORM 호출이 한 줄도 없다.

모든 데이터는 bff_core.api_client 를 통해 /api/v1/admin/... 에서 가져온다.

API 응답을 템플릿이 기대하는 모양으로 다듬는 얇은 층이 뷰의 역할이다.
예를 들어 API 는 선택지를 [{value, label}] 로 주지만 템플릿은
{% for val, lbl in status_choices %} 형태를 쓰므로 여기서 튜플로 바꾼다.
"""

import logging
from datetime import datetime

from django.contrib import messages
from django.http import Http404, HttpResponse
from django.shortcuts import redirect, render

from bff_core.api_client import ApiClient, ApiError, client_for
from bff_core.auth import admin_required, login_session, logout_session

logger = logging.getLogger(__name__)


def _fmt_dt(iso_string, fmt='%Y-%m-%d %H:%M'):
    """API 가 주는 ISO8601 문자열을 표시용으로 포맷한다."""
    if not iso_string:
        return ''
    try:
        return datetime.fromisoformat(iso_string).strftime(fmt)
    except (TypeError, ValueError):
        return str(iso_string)[:16]


def _choices(payload, key='status_choices'):
    """[{value,label}] → [(value,label)]. 템플릿의 언패킹 문법에 맞춘다."""
    return [(c['value'], c['label']) for c in (payload or {}).get(key, [])]


def _safe_next(raw, fallback='/admin-web/'):
    """오픈 리다이렉트 방지: 같은 사이트 경로만 허용한다."""
    if raw and raw.startswith('/') and not raw.startswith('//'):
        return raw
    return fallback


# ------------------------------------------------------------------ 인증
def login_view(request):
    next_url = _safe_next(request.POST.get('next') or request.GET.get('next'))

    if request.web_user.is_authenticated and request.web_user.role == 'admin':
        return redirect(next_url)

    if request.method == 'POST':
        username = (request.POST.get('username') or '').strip()
        password = request.POST.get('password') or ''
        if not username or not password:
            messages.error(request, '아이디와 비밀번호를 입력해주세요.')
            return render(request, 'admin_web/login.html',
                          {'next': next_url, 'hide_chrome': True})
        try:
            payload = client_for(request).login(username, password)
        except ApiError as exc:
            messages.error(request, exc.message)
            return render(request, 'admin_web/login.html',
                          {'next': next_url, 'hide_chrome': True})

        if (payload.get('user') or {}).get('role') != 'admin':
            # 이 컨테이너는 관리자 전용이다. 다른 역할의 토큰은 세션에 남기지 않는다.
            messages.error(request, '관리자 계정만 로그인할 수 있습니다.')
            return render(request, 'admin_web/login.html',
                          {'next': next_url, 'hide_chrome': True})

        login_session(request, payload)
        return redirect(next_url)

    return render(request, 'admin_web/login.html', {'next': next_url, 'hide_chrome': True})


def logout_view(request):
    if request.web_user.is_authenticated:
        logout_session(request)
    messages.success(request, '로그아웃되었습니다.')
    return redirect('admin_web:login')


# ------------------------------------------------------------------ 대시보드
@admin_required
def dashboard(request):
    api = client_for(request)

    if request.method == 'POST' and request.POST.get('action') == 'refresh_baseline':
        try:
            api.post('/admin/dashboard/baseline')
        except ApiError as exc:
            messages.error(request, exc.message)
        return redirect('admin_web:admin_dashboard')

    data = {}
    try:
        data = api.get('/admin/dashboard')
    except ApiError as exc:
        messages.error(request, exc.message)

    for o in data.get('recent_orders', []):
        o['created_display'] = _fmt_dt(o.get('created_at'), '%m/%d %H:%M')
    for u in data.get('recent_users', []):
        u['joined_display'] = _fmt_dt(u.get('date_joined'), '%Y-%m-%d')

    return render(request, 'admin_web/dashboard.html', {
        'cards': data.get('cards', []),
        'baseline_at': _fmt_dt(data.get('baseline_at')),
        'recent_orders': data.get('recent_orders', []),
        'recent_users': data.get('recent_users', []),
    })


# ------------------------------------------------------------------ 회원
@admin_required
def user_list(request):
    api = client_for(request)
    q = (request.GET.get('q') or '').strip()
    role = request.GET.get('role') or ''

    payload = {}
    try:
        params = {}
        if q:
            params['q'] = q
        if role:
            params['role'] = role
        payload = api.get('/admin/users', params=params or None)
    except ApiError as exc:
        messages.error(request, exc.message)

    users = payload.get('results', [])
    for u in users:
        u['joined_display'] = _fmt_dt(u.get('date_joined'), '%Y-%m-%d')

    return render(request, 'admin_web/users.html', {
        'users': users, 'q': q, 'role': role,
        'role_choices': _choices(payload, 'role_choices'),
        'status_choices': _choices(payload),
    })


@admin_required
def user_detail(request, pk):
    api = client_for(request)

    if request.method == 'POST':
        try:
            api.put(f'/admin/users/{pk}/status', json={'status': request.POST.get('status')})
            messages.success(request, '회원 상태를 변경했습니다.')
        except ApiError as exc:
            messages.error(request, exc.message)
        return redirect('admin_web:admin_user_detail', pk=pk)

    try:
        payload = api.get(f'/admin/users/{pk}')
    except ApiError as exc:
        if exc.status_code == 404:
            raise Http404('사용자를 찾을 수 없습니다.')
        messages.error(request, exc.message)
        return redirect('admin_web:admin_users')

    payload['joined_display'] = _fmt_dt(payload.get('date_joined'), '%Y-%m-%d')
    orders = payload.get('recent_orders', [])
    for o in orders:
        o['created_display'] = _fmt_dt(o.get('created_at'), '%Y-%m-%d %H:%M')

    return render(request, 'admin_web/user_detail.html', {
        'obj': payload, 'orders': orders, 'status_choices': _choices(payload),
    })


# ------------------------------------------------------------------ 점주
@admin_required
def owner_list(request):
    api = client_for(request)
    q = (request.GET.get('q') or '').strip()

    payload = {}
    try:
        payload = api.get('/admin/owners', params={'q': q} if q else None)
    except ApiError as exc:
        messages.error(request, exc.message)

    # 템플릿은 {'user': ..., 'restaurants': [...]} 모양을 기대한다.
    rows = [{'user': o, 'restaurants': o.get('restaurants', [])}
            for o in payload.get('results', [])]

    return render(request, 'admin_web/owners.html', {
        'rows': rows, 'q': q, 'status_choices': _choices(payload),
    })


@admin_required
def owner_status(request, pk):
    if request.method == 'POST':
        try:
            client_for(request).put(f'/admin/owners/{pk}/status',
                                    json={'status': request.POST.get('status')})
            messages.success(request, '점주 상태를 변경했습니다.')
        except ApiError as exc:
            messages.error(request, exc.message)
    return redirect('admin_web:admin_owners')


# ------------------------------------------------------------------ 입점 심사
@admin_required
def store_list(request):
    api = client_for(request)
    q = (request.GET.get('q') or '').strip()
    status = request.GET.get('status', 'pending')

    stores = []
    try:
        params = {'status': status}
        if q:
            params['q'] = q
        stores = api.get('/admin/stores', params=params).get('results', [])
    except ApiError as exc:
        messages.error(request, exc.message)

    for s in stores:
        s['created_display'] = _fmt_dt(s.get('created_at'), '%Y-%m-%d')
        s['reviewed_display'] = _fmt_dt(s.get('reviewed_at'))

    return render(request, 'admin_web/store.html', {
        'stores': stores, 'q': q, 'status': status,
        'status_choices': [
            ('pending', '승인 대기'),
            ('approved', '승인 완료'),
            ('rejected', '반려'),
            ('all', '전체'),
        ],
    })


@admin_required
def store_decide(request, pk):
    if request.method == 'POST':
        try:
            # 이미 처리된 건인지, 반려 사유가 있는지는 서버가 판단한다.
            result = client_for(request).post(f'/admin/stores/{pk}/decide', json={
                'action': request.POST.get('action'),
                'rejection_reason': request.POST.get('rejection_reason') or '',
            })
            messages.success(request, result.get('detail', '처리했습니다.'))
        except ApiError as exc:
            messages.error(request, exc.message)
    return redirect('admin_web:admin_store')


# ------------------------------------------------------------------ 탈퇴 승인
@admin_required
def withdrawal_requests(request):
    payload = {}
    try:
        payload = client_for(request).get('/admin/withdrawals')
    except ApiError as exc:
        messages.error(request, exc.message)

    for group in ('pending', 'processed'):
        for r in payload.get(group, []):
            r['requested_display'] = _fmt_dt(r.get('requested_at'))
            r['processed_display'] = _fmt_dt(r.get('processed_at'))

    return render(request, 'admin_web/withdrawals.html', {
        'pending': payload.get('pending', []),
        'processed': payload.get('processed', []),
    })


@admin_required
def withdrawal_decide(request, pk):
    if request.method == 'POST':
        try:
            result = client_for(request).post(f'/admin/withdrawals/{pk}/decide',
                                              json={'action': request.POST.get('action')})
            messages.success(request, result.get('detail', '처리했습니다.'))
        except ApiError as exc:
            messages.error(request, exc.message)
    return redirect('admin_web:admin_withdrawals')


# ------------------------------------------------------- 매장 정보 수정 승인
@admin_required
def restaurant_edit_requests(request):
    payload = {}
    try:
        payload = client_for(request).get('/admin/restaurant-edits')
    except ApiError as exc:
        messages.error(request, exc.message)

    pending = payload.get('pending', [])
    for r in pending:
        r['requested_display'] = _fmt_dt(r.get('requested_at'))
    processed = payload.get('processed', [])
    for r in processed:
        r['requested_display'] = _fmt_dt(r.get('requested_at'))
        r['processed_display'] = _fmt_dt(r.get('processed_at'))

    return render(request, 'admin_web/restaurant_edits.html', {
        'pending': pending, 'processed': processed,
    })


@admin_required
def restaurant_edit_decide(request, pk):
    if request.method == 'POST':
        try:
            result = client_for(request).post(f'/admin/restaurant-edits/{pk}/decide',
                                              json={'action': request.POST.get('action')})
            messages.success(request, result.get('detail', '처리했습니다.'))
        except ApiError as exc:
            messages.error(request, exc.message)
    return redirect('admin_web:admin_restaurant_edits')


# ------------------------------------------------------------------ 주문/결제
@admin_required
def order_list(request):
    status = request.GET.get('status', '')
    payload = {}
    try:
        payload = client_for(request).get('/admin/orders',
                                          params={'status': status} if status else None)
    except ApiError as exc:
        messages.error(request, exc.message)

    orders = payload.get('results', [])
    for o in orders:
        o['created_display'] = _fmt_dt(o.get('created_at'), '%m/%d %H:%M')

    return render(request, 'admin_web/orders.html', {
        'orders': orders, 'status': status, 'status_choices': _choices(payload),
    })


@admin_required
def payment_list(request):
    status = request.GET.get('status', '')
    payload = {}
    try:
        payload = client_for(request).get('/admin/payments',
                                          params={'status': status} if status else None)
    except ApiError as exc:
        messages.error(request, exc.message)

    payments = payload.get('results', [])
    for p in payments:
        p['created_display'] = _fmt_dt(p.get('created_at'), '%m/%d %H:%M')

    return render(request, 'admin_web/payments.html', {
        'payments': payments, 'status': status, 'status_choices': _choices(payload),
    })


# ------------------------------------------------------------------ 공지사항
@admin_required
def notice_list(request):
    notices = []
    try:
        notices = client_for(request).get('/admin/notices').get('results', [])
    except ApiError as exc:
        messages.error(request, exc.message)
    for n in notices:
        n['created_display'] = _fmt_dt(n.get('created_at'), '%Y-%m-%d')
    return render(request, 'admin_web/notices.html', {'notices': notices})


def _notice_payload(request):
    return {
        'title': (request.POST.get('title') or '').strip(),
        'content': (request.POST.get('content') or '').strip(),
        'is_pinned': 'true' if request.POST.get('is_pinned') == 'on' else 'false',
    }


def _notice_files(request):
    files = {}
    image = request.FILES.get('image')
    if image:
        files['image'] = (image.name, image.file, image.content_type)
    attachment = request.FILES.get('attachment')
    if attachment:
        files['attachment'] = (attachment.name, attachment.file, attachment.content_type)
    return files


def _notice_error(exc):
    """DRF 필드 검증 오류를 한 줄로."""
    if exc.message and exc.message != '요청을 처리하지 못했습니다.':
        return exc.message
    payload = exc.payload if isinstance(exc.payload, dict) else {}
    for value in payload.values():
        if isinstance(value, list) and value:
            return str(value[0])
        if isinstance(value, str):
            return value
    return '공지사항을 저장하지 못했습니다.'


@admin_required
def notice_create(request):
    if request.method == 'POST':
        try:
            # 제목·내용·이미지 검증은 서버(NoticeSerializer)가 한다.
            client_for(request).post('/admin/notices',
                                     data=_notice_payload(request),
                                     files=_notice_files(request))
            messages.success(request, '공지사항을 등록했습니다.')
            return redirect('admin_web:admin_notices')
        except ApiError as exc:
            messages.error(request, _notice_error(exc))
    return render(request, 'admin_web/notice_form.html', {'notice': None})


@admin_required
def notice_edit(request, pk):
    api = client_for(request)

    if request.method == 'POST':
        try:
            api.request('PUT', f'/admin/notices/{pk}',
                        data=_notice_payload(request), files=_notice_files(request))
            messages.success(request, '공지사항을 수정했습니다.')
            return redirect('admin_web:admin_notices')
        except ApiError as exc:
            messages.error(request, _notice_error(exc))

    notice = None
    try:
        # 상세 단건 조회는 공개(인증 필요) 엔드포인트를 쓴다.
        notice = api.get(f'/notices/{pk}')
    except ApiError as exc:
        if exc.status_code == 404:
            raise Http404('공지사항을 찾을 수 없습니다.')
        messages.error(request, exc.message)

    return render(request, 'admin_web/notice_form.html', {'notice': notice})


@admin_required
def notice_delete(request, pk):
    if request.method == 'POST':
        try:
            client_for(request).delete(f'/admin/notices/{pk}')
            messages.success(request, '공지사항을 삭제했습니다.')
        except ApiError as exc:
            messages.error(request, exc.message)
    return redirect('admin_web:admin_notices')
