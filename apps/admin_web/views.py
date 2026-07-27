"""관리자(Admin) 웹 화면 - 회원/점주/주문/결제 관리."""

import os

from django.db import transaction
from enrollment.models import EnrollmentRequest

from django.contrib import messages
from django.contrib.auth import get_user_model
from django.db.models import Q, Sum
from django.shortcuts import get_object_or_404, redirect, render
from django.utils import timezone

from accounts.models import WithdrawalRequest
from adminpanel.models import MetricSnapshot, Notice
from orders.models import Order
from payments.models import Payment
from restaurants.models import Restaurant, RestaurantEditRequest

from .decorators import admin_required

_ALLOWED_NOTICE_IMAGE_EXTENSIONS = {'.jpg', '.jpeg', '.png', '.gif', '.webp'}
_MAX_NOTICE_IMAGE_SIZE = 5 * 1024 * 1024  # 5MB


def login_view(request):
    """관리자 전용 로그인.

    apps/web/views/auth.py의 login_view 중 admin 분기만 떼어온 것.
    이 컨테이너(admin-web)는 admin_required 뷰만 갖고 있으므로,
    owner 계정으로 로그인해도 어차피 아무 화면도 못 보므로 여기서 role을
    admin으로 한 번 더 제한한다.
    """
    from django.contrib import messages as django_messages
    from django.contrib.auth import authenticate, login as auth_login
    from django.contrib.auth import get_user_model as _get_user_model
    from django.shortcuts import redirect as _redirect, render as _render

    _User = _get_user_model()

    if request.user.is_authenticated and request.user.role == _User.Role.ADMIN:
        return _redirect('admin_web:admin_dashboard')

    if request.method == 'POST':
        username = request.POST.get('username', '').strip()
        password = request.POST.get('password', '')

        user = authenticate(request, username=username, password=password)

        if user is None:
            django_messages.error(request, '아이디 또는 비밀번호가 올바르지 않습니다.')
        elif user.role != _User.Role.ADMIN:
            django_messages.error(request, '관리자 계정만 로그인할 수 있습니다.')
        elif user.status != _User.Status.ACTIVE or not user.is_active:
            django_messages.error(request, '비활성화된 계정입니다.')
        else:
            auth_login(request, user)
            nxt = request.GET.get('next') or request.POST.get('next')
            if nxt:
                return _redirect(nxt)
            return _redirect('admin_web:admin_dashboard')

    return _render(
        request,
        'admin_web/login.html',
        {'next': request.GET.get('next', ''), 'hide_chrome': True},
    )


def logout_view(request):
    from django.contrib import messages as django_messages
    from django.contrib.auth import logout as auth_logout
    from django.shortcuts import redirect as _redirect

    auth_logout(request)
    django_messages.success(request, '로그아웃되었습니다.')
    return _redirect('admin_web:login')


def _valid_notice_image(upload):
    extension = os.path.splitext(upload.name)[1].lower()
    return extension in _ALLOWED_NOTICE_IMAGE_EXTENSIONS and upload.size <= _MAX_NOTICE_IMAGE_SIZE

User = get_user_model()
USER_STATUS = dict(User.Status.choices)

RESTAURANT_FIELD_LABELS = {
    'name': '매장명', 'cuisine_type': '종류', 'description': '소개',
    'phone': '연락처', 'address': '주소',
    'min_order_amount': '최소주문금액', 'delivery_fee': '배달비', 'is_open': '영업상태',
}


def _restaurant_display(field, value):
    if field == 'cuisine_type' and value:
        return value.replace(',', ', ')
    return value


def _restaurant_diff_rows(edit_request):
    rows = []
    for field, new_value in edit_request.changes.items():
        rows.append({
            'label': RESTAURANT_FIELD_LABELS.get(field, field),
            'old': _restaurant_display(field, getattr(edit_request.restaurant, field)),
            'new': _restaurant_display(field, new_value),
        })
    return rows

def _store_review_status(store):
    """이 매장의 심사 상태. 'pending'/'approved'/'rejected'로 통일해서 반환한다.

    add_restaurant로 만든 매장은 EnrollmentRequest가 즉시 생성되므로 그걸 그대로 쓰고,
    signup_view의 최초 매장은 아직 EnrollmentRequest가 없을 수 있어 owner.status로 판단한다.
    """
    enrollment = getattr(store, 'enrollment_request', None)
    if enrollment:
        return enrollment.status

    mapping = {
        User.Status.PENDING: 'pending',
        User.Status.ACTIVE: 'approved',
        User.Status.SUSPENDED: 'rejected',
    }
    return mapping.get(store.owner.status, 'pending')

# (key, label, is_money, unit) — unit 은 값 뒤에 붙는 단위(예: '2명', '3개')
_DASHBOARD_METRIC_CARDS = [
    ('user_count', '일반 회원', False, '명'),
    ('owner_count', '점주', False, '명'),
    ('restaurant_count', '등록 매장', False, '개'),
    ('order_count', '전체 주문', False, '개'),
    ('suspended_count', '정지 계정', False, '개'),
    ('total_sales', '결제 완료액', True, '원'),
]


@admin_required
def dashboard(request):
    metrics = {
        'user_count': User.objects.filter(role=User.Role.CUSTOMER).count(),
        'owner_count': User.objects.filter(role=User.Role.OWNER).count(),
        'restaurant_count': Restaurant.objects.count(),
        'order_count': Order.objects.count(),
        'suspended_count': User.objects.filter(status=User.Status.SUSPENDED).count(),
        'total_sales': Payment.objects.filter(status=Payment.Status.PAID).aggregate(s=Sum('amount'))['s'] or 0,
    }

    # '기준 갱신' 버튼: 현재 수치를 새 비교 기준 스냅샷으로 저장한다.
    # 갱신 시각은 상단 '갱신되었습니다 {시각}' 라벨로 바로 확인되므로 별도 알림은 띄우지 않는다.
    if request.method == 'POST' and request.POST.get('action') == 'refresh_baseline':
        MetricSnapshot.objects.create(created_by=request.user, **metrics)
        return redirect('admin_web:admin_dashboard')

    baseline = MetricSnapshot.objects.first()  # ordering = ['-created_at'] → 최신 스냅샷

    cards = []
    for key, label, is_money, unit in _DASHBOARD_METRIC_CARDS:
        value = metrics[key]
        card = {'label': label, 'value': value, 'is_money': is_money, 'unit': unit, 'has_delta': False}
        if baseline is not None:
            diff = value - getattr(baseline, key)
            card['has_delta'] = True
            card['diff_abs'] = abs(diff)
            card['direction'] = 'up' if diff > 0 else ('down' if diff < 0 else 'flat')
        cards.append(card)

    ctx = {
        'cards': cards,
        'baseline_at': baseline.created_at if baseline else None,
        'recent_orders': Order.objects.select_related('restaurant', 'user')[:8],
        'recent_users': User.objects.order_by('-date_joined')[:8],
    }
    return render(request, 'admin_web/dashboard.html', ctx)


@admin_required
def user_list(request):
    q = request.GET.get('q', '').strip()
    role = request.GET.get('role', '')
    users = User.objects.all()
    if role:
        users = users.filter(role=role)
    if q:
        users = users.filter(Q(username__icontains=q) | Q(email__icontains=q) | Q(nickname__icontains=q))
    return render(request, 'admin_web/users.html', {
        'users': users.order_by('-date_joined')[:300],
        'q': q, 'role': role,
        'role_choices': User.Role.choices,
        'status_choices': User.Status.choices,
    })


@admin_required
def user_detail(request, pk):
    user = get_object_or_404(User, pk=pk)
    if request.method == 'POST':
        new_status = request.POST.get('status')
        if new_status in USER_STATUS:
            user.status = new_status
            user.is_active = new_status == User.Status.ACTIVE
            user.save(update_fields=['status', 'is_active'])
            messages.success(request, '회원 상태를 변경했습니다.')
        return redirect('admin_web:admin_user_detail', pk=pk)
    orders = Order.objects.filter(user=user).select_related('restaurant')[:20]
    return render(request, 'admin_web/user_detail.html', {
        'obj': user, 'orders': orders, 'status_choices': User.Status.choices,
    })


@admin_required
def owner_list(request):
    owners = User.objects.filter(role=User.Role.OWNER).order_by('-date_joined')
    q = request.GET.get('q', '').strip()
    if q:
        owners = owners.filter(Q(username__icontains=q) | Q(email__icontains=q))
    # 점주별 매장 수
    rows = []
    for o in owners[:300]:
        rows.append({'user': o, 'restaurants': Restaurant.objects.filter(owner=o)})
    return render(request, 'admin_web/owners.html', {
        'rows': rows, 'q': q, 'status_choices': User.Status.choices,
    })


@admin_required
def owner_status(request, pk):
    owner = get_object_or_404(User, pk=pk, role=User.Role.OWNER)
    if request.method == 'POST':
        new_status = request.POST.get('status')
        if new_status in USER_STATUS:
            owner.status = new_status
            owner.is_active = new_status == User.Status.ACTIVE
            owner.save(update_fields=['status', 'is_active'])
            messages.success(request, '점주 상태를 변경했습니다.')
    return redirect('admin_web:admin_owners')

@admin_required
def store_list(request):
    """입점 신청 매장 목록."""

    q = request.GET.get('q', '').strip()
    status = request.GET.get('status', 'pending')

    stores = (
        Restaurant.objects
        .select_related('owner', 'enrollment_request')
        .filter(owner__role=User.Role.OWNER)
        .order_by('-created_at')
    )

    if q:
        stores = stores.filter(
            Q(owner__username__icontains=q)
            | Q(owner__email__icontains=q)
            | Q(owner__nickname__icontains=q)
            | Q(name__icontains=q)
            | Q(address__icontains=q)
        )

    stores = list(stores[:300])
    for s in stores:
        s.display_status = _store_review_status(s)

    if status != 'all':
        stores = [s for s in stores if s.display_status == status]

    return render(
        request,
        'admin_web/store.html',
        {
            'stores': stores,
            'q': q,
            'status': status,
            'status_choices': [
                ('pending', '승인 대기'),
                ('approved', '승인 완료'),
                ('rejected', '반려'),
                ('all', '전체'),
            ],
        },
    )


@admin_required
def store_decide(request, pk):
    """관리자가 입점 신청을 승인하거나 반려한다."""

    store = get_object_or_404(
        Restaurant.objects.select_related('owner'),
        pk=pk,
        owner__role=User.Role.OWNER,
    )

    if request.method != 'POST':
        return redirect('admin_web:admin_store')

    owner = store.owner
    action = request.POST.get('action')
    rejection_reason = request.POST.get(
        'rejection_reason',
        '',
    ).strip()

    if _store_review_status(store) != 'pending':
        messages.error(
            request,
            '이미 처리된 입점 신청입니다.',
        )
        return redirect('admin_web:admin_store')

    if action not in ('approve', 'reject'):
        messages.error(
            request,
            '올바르지 않은 처리 요청입니다.',
        )
        return redirect('admin_web:admin_store')

    if action == 'reject' and not rejection_reason:
        messages.error(
            request,
            '반려 사유를 입력해주세요.',
        )
        return redirect('admin_web:admin_store')

    if len(rejection_reason) > 500:
        messages.error(
            request,
            '반려 사유는 500자 이하로 입력해주세요.',
        )
        return redirect('admin_web:admin_store')

    with transaction.atomic():
        enrollment_status = 'approved' if action == 'approve' else 'rejected'

        # 이 점주의 "최초" 매장(계정 자체가 아직 pending)인 경우에만 계정 상태도 같이 바꾼다.
        if owner.status == User.Status.PENDING:
            owner.status = User.Status.ACTIVE if action == 'approve' else User.Status.SUSPENDED
            owner.is_active = action == 'approve'
            owner.save(update_fields=['status', 'is_active'])

        defaults = {
            'username': owner.username,
            'phone': (owner.phone or '')[:20],
            'owner_name': owner.nickname or owner.username,
            'restaurant_name': store.name,
            'status': enrollment_status,
            'reviewed_at': timezone.now(),
            'reviewed_by': request.user,
        }

        if action == 'reject':
            defaults['rejection_reason'] = rejection_reason

        EnrollmentRequest.objects.update_or_create(
            restaurant=store,
            defaults=defaults,
        )

    if action == 'approve':
        messages.success(
            request,
            f'{store.name}의 입점을 승인했습니다.',
        )
    else:
        messages.success(
            request,
            f'{store.name}의 입점 신청을 반려했습니다.',
        )

    return redirect('admin_web:admin_store')

@admin_required
def withdrawal_requests(request):
    pending = WithdrawalRequest.objects.select_related('user').filter(
        status=WithdrawalRequest.Status.PENDING,
    )
    processed = WithdrawalRequest.objects.select_related('user', 'processed_by').exclude(
        status=WithdrawalRequest.Status.PENDING,
    )[:50]
    return render(request, 'admin_web/withdrawals.html', {'pending': pending, 'processed': processed})


@admin_required
def withdrawal_decide(request, pk):
    wr = get_object_or_404(WithdrawalRequest, pk=pk, status=WithdrawalRequest.Status.PENDING)
    if request.method == 'POST':
        action = request.POST.get('action')
        if action == 'approve':
            wr.status = WithdrawalRequest.Status.APPROVED
            wr.user.status = wr.user.Status.WITHDRAWN
            wr.user.is_active = False
            wr.user.save(update_fields=['status', 'is_active'])
            messages.success(request, f'{wr.user.username} 님의 탈퇴를 승인했습니다.')
        elif action == 'reject':
            wr.status = WithdrawalRequest.Status.REJECTED
            messages.success(request, f'{wr.user.username} 님의 탈퇴 요청을 거절했습니다.')
        else:
            return redirect('admin_web:admin_withdrawals')
        wr.processed_at = timezone.now()
        wr.processed_by = request.user
        wr.save(update_fields=['status', 'processed_at', 'processed_by'])
    return redirect('admin_web:admin_withdrawals')


@admin_required
def restaurant_edit_requests(request):
    pending_qs = RestaurantEditRequest.objects.select_related('restaurant', 'requested_by').filter(
        status=RestaurantEditRequest.Status.PENDING,
    )
    processed_qs = RestaurantEditRequest.objects.select_related(
        'restaurant', 'requested_by', 'processed_by',
    ).exclude(status=RestaurantEditRequest.Status.PENDING)[:50]

    pending = [{'req': r, 'rows': _restaurant_diff_rows(r)} for r in pending_qs]
    return render(request, 'admin_web/restaurant_edits.html', {
        'pending': pending, 'processed': processed_qs,
    })


@admin_required
def restaurant_edit_decide(request, pk):
    er = get_object_or_404(RestaurantEditRequest, pk=pk, status=RestaurantEditRequest.Status.PENDING)
    if request.method == 'POST':
        action = request.POST.get('action')
        if action == 'approve':
            for field, value in er.changes.items():
                setattr(er.restaurant, field, value)
            er.restaurant.save(update_fields=list(er.changes.keys()))
            er.status = RestaurantEditRequest.Status.APPROVED
            messages.success(request, f'{er.restaurant.name} 매장 정보 수정을 승인했습니다.')
        elif action == 'reject':
            er.status = RestaurantEditRequest.Status.REJECTED
            messages.success(request, f'{er.restaurant.name} 매장 정보 수정 요청을 거절했습니다.')
        else:
            return redirect('admin_web:admin_restaurant_edits')
        er.processed_at = timezone.now()
        er.processed_by = request.user
        er.save(update_fields=['status', 'processed_at', 'processed_by'])
    return redirect('admin_web:admin_restaurant_edits')


@admin_required
def order_list(request):
    orders = Order.objects.select_related('restaurant', 'user').all()
    status = request.GET.get('status', '')
    if status:
        orders = orders.filter(status=status)
    return render(request, 'admin_web/orders.html', {
        'orders': orders[:300], 'status': status, 'status_choices': Order.Status.choices,
    })


@admin_required
def payment_list(request):
    payments = Payment.objects.select_related('order', 'order__restaurant').all()
    status = request.GET.get('status', '')
    if status:
        payments = payments.filter(status=status)
    return render(request, 'admin_web/payments.html', {
        'payments': payments[:300], 'status': status, 'status_choices': Payment.Status.choices,
    })


# ------------------------------------------------------------------ 공지사항
@admin_required
def notice_list(request):
    """앱 전체 사용자에게 노출되는 공지사항 목록."""
    notices = Notice.objects.all()
    return render(request, 'admin_web/notices.html', {'notices': notices})


@admin_required
def notice_create(request):
    """공지사항 작성. 사진은 선택 사항."""
    if request.method == 'POST':
        title = request.POST.get('title', '').strip()
        content = request.POST.get('content', '').strip()
        image = request.FILES.get('image')
        is_pinned = request.POST.get('is_pinned') == 'on'

        if not title:
            messages.error(request, '제목을 입력하세요.')
        elif not content:
            messages.error(request, '내용을 입력하세요.')
        elif image and not _valid_notice_image(image):
            messages.error(request, '이미지는 jpg/png/gif/webp, 5MB 이하만 가능합니다.')
        else:
            Notice.objects.create(
                title=title, content=content, image=image, is_pinned=is_pinned,
                created_by=request.user,
            )
            messages.success(request, '공지사항을 등록했습니다.')
            return redirect('admin_web:admin_notices')
    return render(request, 'admin_web/notice_form.html', {'notice': None})


@admin_required
def notice_edit(request, pk):
    """공지사항 수정. 사진을 새로 첨부하지 않으면 기존 사진을 유지한다."""
    notice = get_object_or_404(Notice, pk=pk)
    if request.method == 'POST':
        title = request.POST.get('title', '').strip()
        content = request.POST.get('content', '').strip()
        image = request.FILES.get('image')
        is_pinned = request.POST.get('is_pinned') == 'on'

        if not title:
            messages.error(request, '제목을 입력하세요.')
        elif not content:
            messages.error(request, '내용을 입력하세요.')
        elif image and not _valid_notice_image(image):
            messages.error(request, '이미지는 jpg/png/gif/webp, 5MB 이하만 가능합니다.')
        else:
            notice.title = title
            notice.content = content
            notice.is_pinned = is_pinned
            if image:
                notice.image = image
            notice.save()
            messages.success(request, '공지사항을 수정했습니다.')
            return redirect('admin_web:admin_notices')
    return render(request, 'admin_web/notice_form.html', {'notice': notice})


@admin_required
def notice_delete(request, pk):
    notice = get_object_or_404(Notice, pk=pk)
    if request.method == 'POST':
        notice.delete()
        messages.success(request, '공지사항을 삭제했습니다.')
    return redirect('admin_web:admin_notices')
