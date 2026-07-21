"""관리자(Admin) 웹 화면 - 회원/점주/주문/결제 관리."""

from django.db import transaction
from enrollment.models import EnrollmentRequest

from django.contrib import messages
from django.contrib.auth import get_user_model
from django.db.models import Q, Sum
from django.shortcuts import get_object_or_404, redirect, render
from django.utils import timezone

from accounts.models import WithdrawalRequest
from orders.models import Order
from payments.models import Payment
from restaurants.models import Restaurant, RestaurantEditRequest

from ..decorators import admin_required

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


@admin_required
def dashboard(request):
    ctx = {
        'user_count': User.objects.filter(role=User.Role.CUSTOMER).count(),
        'owner_count': User.objects.filter(role=User.Role.OWNER).count(),
        'restaurant_count': Restaurant.objects.count(),
        'order_count': Order.objects.count(),
        'suspended_count': User.objects.filter(status=User.Status.SUSPENDED).count(),
        'total_sales': Payment.objects.filter(status=Payment.Status.PAID).aggregate(s=Sum('amount'))['s'] or 0,
        'recent_orders': Order.objects.select_related('restaurant', 'user')[:8],
        'recent_users': User.objects.order_by('-date_joined')[:8],
    }
    return render(request, 'web/admin/dashboard.html', ctx)


@admin_required
def user_list(request):
    q = request.GET.get('q', '').strip()
    role = request.GET.get('role', '')
    users = User.objects.all()
    if role:
        users = users.filter(role=role)
    if q:
        users = users.filter(Q(username__icontains=q) | Q(email__icontains=q) | Q(nickname__icontains=q))
    return render(request, 'web/admin/users.html', {
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
        return redirect('web:admin_user_detail', pk=pk)
    orders = Order.objects.filter(user=user).select_related('restaurant')[:20]
    return render(request, 'web/admin/user_detail.html', {
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
    return render(request, 'web/admin/owners.html', {
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
    return redirect('web:admin_owners')

@admin_required
def store_list(request):
    """입점 신청 매장 목록."""

    q = request.GET.get('q', '').strip()
    status = request.GET.get(
        'status',
        User.Status.PENDING,
    )

    stores = (
        Restaurant.objects
        .select_related('owner', 'enrollment_request')
        .filter(owner__role=User.Role.OWNER)
        .order_by('-created_at')
    )

    if status != 'all':
        stores = stores.filter(owner__status=status)

    if q:
        stores = stores.filter(
            Q(owner__username__icontains=q)
            | Q(owner__email__icontains=q)
            | Q(owner__nickname__icontains=q)
            | Q(name__icontains=q)
            | Q(address__icontains=q)
        )

    return render(
        request,
        'web/admin/store.html',
        {
            'stores': stores[:300],
            'q': q,
            'status': status,
            'status_choices': User.Status.choices,
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
        return redirect('web:admin_store')

    owner = store.owner
    action = request.POST.get('action')
    rejection_reason = request.POST.get(
        'rejection_reason',
        '',
    ).strip()

    if owner.status != User.Status.PENDING:
        messages.error(
            request,
            '이미 처리된 입점 신청입니다.',
        )
        return redirect('web:admin_store')

    if action not in ('approve', 'reject'):
        messages.error(
            request,
            '올바르지 않은 처리 요청입니다.',
        )
        return redirect('web:admin_store')

    if action == 'reject' and not rejection_reason:
        messages.error(
            request,
            '반려 사유를 입력해주세요.',
        )
        return redirect('web:admin_store')

    if len(rejection_reason) > 500:
        messages.error(
            request,
            '반려 사유는 500자 이하로 입력해주세요.',
        )
        return redirect('web:admin_store')

    with transaction.atomic():
        if action == 'approve':
            owner.status = User.Status.ACTIVE
            owner.is_active = True

            enrollment_status = 'approved'
            rejection_reason = ''

        else:
            owner.status = User.Status.SUSPENDED
            owner.is_active = False

            enrollment_status = 'rejected'

        owner.save(
            update_fields=['status', 'is_active'],
        )

        EnrollmentRequest.objects.update_or_create(
            username=owner.username,
            defaults={
                'restaurant': store,
                'phone': (owner.phone or '')[:20],
                'owner_name': owner.nickname or owner.username,
                'restaurant_name': store.name,
                'status': enrollment_status,
                'rejection_reason': rejection_reason,
                'reviewed_at': timezone.now(),
                'reviewed_by': request.user,
            },
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

    return redirect('web:admin_store')

@admin_required
def withdrawal_requests(request):
    pending = WithdrawalRequest.objects.select_related('user').filter(
        status=WithdrawalRequest.Status.PENDING,
    )
    processed = WithdrawalRequest.objects.select_related('user', 'processed_by').exclude(
        status=WithdrawalRequest.Status.PENDING,
    )[:50]
    return render(request, 'web/admin/withdrawals.html', {'pending': pending, 'processed': processed})


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
            return redirect('web:admin_withdrawals')
        wr.processed_at = timezone.now()
        wr.processed_by = request.user
        wr.save(update_fields=['status', 'processed_at', 'processed_by'])
    return redirect('web:admin_withdrawals')


@admin_required
def restaurant_edit_requests(request):
    pending_qs = RestaurantEditRequest.objects.select_related('restaurant', 'requested_by').filter(
        status=RestaurantEditRequest.Status.PENDING,
    )
    processed_qs = RestaurantEditRequest.objects.select_related(
        'restaurant', 'requested_by', 'processed_by',
    ).exclude(status=RestaurantEditRequest.Status.PENDING)[:50]

    pending = [{'req': r, 'rows': _restaurant_diff_rows(r)} for r in pending_qs]
    return render(request, 'web/admin/restaurant_edits.html', {
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
            return redirect('web:admin_restaurant_edits')
        er.processed_at = timezone.now()
        er.processed_by = request.user
        er.save(update_fields=['status', 'processed_at', 'processed_by'])
    return redirect('web:admin_restaurant_edits')


@admin_required
def order_list(request):
    orders = Order.objects.select_related('restaurant', 'user').all()
    status = request.GET.get('status', '')
    if status:
        orders = orders.filter(status=status)
    return render(request, 'web/admin/orders.html', {
        'orders': orders[:300], 'status': status, 'status_choices': Order.Status.choices,
    })


@admin_required
def payment_list(request):
    payments = Payment.objects.select_related('order', 'order__restaurant').all()
    status = request.GET.get('status', '')
    if status:
        payments = payments.filter(status=status)
    return render(request, 'web/admin/payments.html', {
        'payments': payments[:300], 'status': status, 'status_choices': Payment.Status.choices,
    })
