"""관리자 승인·심사 API (/api/v1/admin/...).

탈퇴 승인, 매장 정보 수정 승인, 입점 심사, 대시보드 지표 — 그동안
apps/admin_web 화면에만 존재하던 흐름을 API 로 승격한 것이다.

승인 로직을 서버에 두는 이유는 단순히 화면을 옮기기 위해서가 아니다.
'이미 처리된 건을 다시 처리할 수 없다', '반려에는 사유가 필요하다' 같은
규칙이 화면에만 있으면 API 를 직접 호출해 우회할 수 있다.
"""

from django.contrib.auth import get_user_model
from django.db import transaction
from django.db.models import Q, Sum
from django.utils import timezone
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from accounts.models import WithdrawalRequest
from common.exceptions import error_response
from common.permissions import IsAdminRole
from enrollment.models import EnrollmentRequest
from orders.models import Order
from payments.models import Payment
from restaurants.models import Restaurant, RestaurantEditRequest

from .models import MetricSnapshot, Notice  # noqa: F401  (Notice 는 urls 호환용)

User = get_user_model()

# 매장 정보 수정 요청 화면에 보여줄 필드 라벨.
RESTAURANT_FIELD_LABELS = {
    'name': '매장명', 'cuisine_type': '종류', 'description': '소개',
    'phone': '연락처', 'address': '주소',
    'min_order_amount': '최소주문금액', 'delivery_fee': '배달비', 'is_open': '영업상태',
}

MAX_REJECTION_REASON = 500

# 대시보드 카드 정의. (key, label, is_money, unit)
DASHBOARD_CARDS = [
    ('user_count', '일반 회원', False, '명'),
    ('owner_count', '점주', False, '명'),
    ('restaurant_count', '등록 매장', False, '개'),
    ('order_count', '전체 주문', False, '개'),
    ('suspended_count', '정지 계정', False, '개'),
    ('total_sales', '결제 완료액', True, '원'),
]


# --------------------------------------------------------------------- 공통
def _user_brief(user):
    if not user:
        return None
    return {
        'id': user.id,
        'username': user.username,
        'nickname': user.nickname,
        'display_name': user.nickname or user.username,
        'email': user.email,
        'phone': user.phone,
        'role': user.role,
        'role_display': user.get_role_display(),
        'status': user.status,
        'status_display': user.get_status_display(),
        'date_joined': user.date_joined,
    }


def _store_review_status(store):
    """매장의 심사 상태를 'pending'/'approved'/'rejected' 로 통일해 돌려준다.

    나중에 추가한 매장은 EnrollmentRequest 가 바로 생기므로 그걸 쓰고,
    회원가입 시 만들어진 최초 매장은 아직 없을 수 있어 소유자 계정 상태로 판단한다.
    """
    enrollment = getattr(store, 'enrollment_request', None)
    if enrollment:
        return enrollment.status
    mapping = {
        User.Status.PENDING: 'pending',
        User.Status.ACTIVE: 'approved',
        User.Status.SUSPENDED: 'rejected',
    }
    return mapping.get(store.owner.status if store.owner else None, 'pending')


def _restaurant_display(field, value):
    if field == 'cuisine_type' and value:
        return str(value).replace(',', ', ')
    return value


def _diff_rows(edit_request):
    """수정 요청의 변경 전/후를 화면이 그대로 그릴 수 있는 형태로 만든다."""
    rows = []
    for field, new_value in (edit_request.changes or {}).items():
        rows.append({
            'label': RESTAURANT_FIELD_LABELS.get(field, field),
            'field': field,
            'old': _restaurant_display(field, getattr(edit_request.restaurant, field, '')),
            'new': _restaurant_display(field, new_value),
        })
    return rows


def _decide_action(request):
    """approve / reject 와 반려 사유를 검증해 돌려준다."""
    action = request.data.get('action')
    if action not in ('approve', 'reject'):
        return None, None, error_response('bad_request', '올바르지 않은 처리 요청입니다.', 400)

    reason = (request.data.get('rejection_reason') or '').strip()
    if action == 'reject':
        if not reason:
            return None, None, error_response('reason_required', '반려 사유를 입력해주세요.', 400)
        if len(reason) > MAX_REJECTION_REASON:
            return None, None, error_response(
                'reason_too_long', f'반려 사유는 {MAX_REJECTION_REASON}자 이하로 입력해주세요.', 400,
            )
    return action, reason, None


# --------------------------------------------------------------------- 대시보드
def _current_metrics():
    return {
        'user_count': User.objects.filter(role=User.Role.CUSTOMER).count(),
        'owner_count': User.objects.filter(role=User.Role.OWNER).count(),
        'restaurant_count': Restaurant.objects.count(),
        'order_count': Order.objects.count(),
        'suspended_count': User.objects.filter(status=User.Status.SUSPENDED).count(),
        'total_sales': Payment.objects.filter(status=Payment.Status.PAID)
                       .aggregate(s=Sum('amount'))['s'] or 0,
    }


@api_view(['GET'])
@permission_classes([IsAdminRole])
def admin_dashboard(request):
    """지표 카드 + 최근 주문/가입. 화면 하나를 그리는 데 필요한 것을 한 번에 준다."""
    metrics = _current_metrics()
    baseline = MetricSnapshot.objects.first()   # ordering = ['-created_at']

    cards = []
    for key, label, is_money, unit in DASHBOARD_CARDS:
        value = metrics[key]
        card = {'key': key, 'label': label, 'value': value,
                'is_money': is_money, 'unit': unit, 'has_delta': False}
        if baseline is not None:
            diff = value - getattr(baseline, key)
            card.update({
                'has_delta': True,
                'diff_abs': abs(diff),
                'direction': 'up' if diff > 0 else ('down' if diff < 0 else 'flat'),
            })
        cards.append(card)

    recent_orders = Order.objects.select_related('restaurant', 'user')[:8]
    recent_users = User.objects.order_by('-date_joined')[:8]

    return Response({
        'cards': cards,
        'baseline_at': baseline.created_at if baseline else None,
        'recent_orders': [
            {
                'id': o.id, 'order_number': o.order_number,
                'restaurant_name': o.restaurant.name if o.restaurant else '',
                'customer_name': (o.user.nickname or o.user.username) if o.user else '',
                'total': o.total, 'status': o.status,
                'status_display': o.get_status_display(), 'created_at': o.created_at,
            }
            for o in recent_orders
        ],
        'recent_users': [_user_brief(u) for u in recent_users],
    })


@api_view(['POST'])
@permission_classes([IsAdminRole])
def admin_dashboard_baseline(request):
    """현재 수치를 새 비교 기준으로 저장한다."""
    snapshot = MetricSnapshot.objects.create(created_by=request.user, **_current_metrics())
    return Response({'baseline_at': snapshot.created_at}, status=201)


# --------------------------------------------------------------------- 탈퇴 승인
@api_view(['GET'])
@permission_classes([IsAdminRole])
def admin_withdrawal_list(request):
    pending = WithdrawalRequest.objects.select_related('user').filter(
        status=WithdrawalRequest.Status.PENDING,
    )
    processed = WithdrawalRequest.objects.select_related('user', 'processed_by').exclude(
        status=WithdrawalRequest.Status.PENDING,
    )[:50]

    def _row(wr):
        return {
            'id': wr.id,
            'user': _user_brief(wr.user),
            'status': wr.status,
            'status_display': wr.get_status_display(),
            'requested_at': wr.requested_at,
            'processed_at': wr.processed_at,
            'processed_by': wr.processed_by.username if wr.processed_by else '',
        }

    return Response({
        'pending': [_row(w) for w in pending],
        'processed': [_row(w) for w in processed],
    })


@api_view(['POST'])
@permission_classes([IsAdminRole])
def admin_withdrawal_decide(request, pk):
    """탈퇴 요청 승인/거절. 이미 처리된 건은 다시 처리할 수 없다."""
    wr = WithdrawalRequest.objects.select_related('user').filter(
        pk=pk, status=WithdrawalRequest.Status.PENDING,
    ).first()
    if not wr:
        return error_response('not_found', '처리 대기 중인 탈퇴 요청이 아닙니다.', 404)

    action = request.data.get('action')
    if action not in ('approve', 'reject'):
        return error_response('bad_request', '올바르지 않은 처리 요청입니다.', 400)

    with transaction.atomic():
        if action == 'approve':
            wr.status = WithdrawalRequest.Status.APPROVED
            wr.user.status = User.Status.WITHDRAWN
            wr.user.is_active = False
            wr.user.save(update_fields=['status', 'is_active'])
            detail = f'{wr.user.username} 님의 탈퇴를 승인했습니다.'
        else:
            wr.status = WithdrawalRequest.Status.REJECTED
            detail = f'{wr.user.username} 님의 탈퇴 요청을 거절했습니다.'

        wr.processed_at = timezone.now()
        wr.processed_by = request.user
        wr.save(update_fields=['status', 'processed_at', 'processed_by'])

    return Response({'detail': detail, 'status': wr.status})


# --------------------------------------------------------- 매장 정보 수정 승인
@api_view(['GET'])
@permission_classes([IsAdminRole])
def admin_restaurant_edit_list(request):
    pending = RestaurantEditRequest.objects.select_related('restaurant', 'requested_by').filter(
        status=RestaurantEditRequest.Status.PENDING,
    )
    processed = RestaurantEditRequest.objects.select_related(
        'restaurant', 'requested_by', 'processed_by',
    ).exclude(status=RestaurantEditRequest.Status.PENDING)[:50]

    return Response({
        'pending': [
            {
                'id': r.id,
                'restaurant_name': r.restaurant.name if r.restaurant else '',
                'requested_by': r.requested_by.username if r.requested_by else '',
                'requested_at': r.requested_at,
                'rows': _diff_rows(r),
            }
            for r in pending
        ],
        'processed': [
            {
                'id': r.id,
                'restaurant_name': r.restaurant.name if r.restaurant else '',
                'status': r.status,
                'status_display': r.get_status_display(),
                'requested_at': r.requested_at,
                'processed_at': r.processed_at,
                'processed_by': r.processed_by.username if r.processed_by else '',
            }
            for r in processed
        ],
    })


@api_view(['POST'])
@permission_classes([IsAdminRole])
def admin_restaurant_edit_decide(request, pk):
    """수정 요청 승인 시에만 실제 Restaurant 레코드에 반영한다."""
    er = RestaurantEditRequest.objects.select_related('restaurant').filter(
        pk=pk, status=RestaurantEditRequest.Status.PENDING,
    ).first()
    if not er:
        return error_response('not_found', '처리 대기 중인 수정 요청이 아닙니다.', 404)

    action = request.data.get('action')
    if action not in ('approve', 'reject'):
        return error_response('bad_request', '올바르지 않은 처리 요청입니다.', 400)

    with transaction.atomic():
        if action == 'approve':
            changes = er.changes or {}
            # 화면에서 온 값을 그대로 쓰지 않고, 허용된 필드만 반영한다.
            allowed = [f for f in changes if f in RESTAURANT_FIELD_LABELS]
            for field in allowed:
                setattr(er.restaurant, field, changes[field])
            if allowed:
                er.restaurant.save(update_fields=allowed)
            er.status = RestaurantEditRequest.Status.APPROVED
            detail = f'{er.restaurant.name} 매장 정보 수정을 승인했습니다.'
        else:
            er.status = RestaurantEditRequest.Status.REJECTED
            detail = f'{er.restaurant.name} 매장 정보 수정 요청을 거절했습니다.'

        er.processed_at = timezone.now()
        er.processed_by = request.user
        er.save(update_fields=['status', 'processed_at', 'processed_by'])

    return Response({'detail': detail, 'status': er.status})


# --------------------------------------------------------------------- 입점 심사
@api_view(['GET'])
@permission_classes([IsAdminRole])
def admin_store_list(request):
    """입점 신청 매장 목록. ?status=pending|approved|rejected|all &q=검색어

    기존 /enrollment/list 는 EnrollmentRequest 를 기준으로 하는데, 회원가입 시
    만들어진 최초 매장은 그 레코드가 없을 수 있어 목록에서 누락된다.
    이 엔드포인트는 매장을 기준으로 삼아 그 경우까지 포함한다.
    """
    q = (request.query_params.get('q') or '').strip()
    status = request.query_params.get('status', 'pending')

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

    rows = []
    for s in stores[:300]:
        review_status = _store_review_status(s)
        if status != 'all' and review_status != status:
            continue
        enrollment = getattr(s, 'enrollment_request', None)
        rows.append({
            'id': s.id,
            'name': s.name,
            'address': s.address,
            'phone': s.phone,
            'created_at': s.created_at,
            'business_license': bool(s.business_license),
            'review_status': review_status,
            'rejection_reason': getattr(enrollment, 'rejection_reason', '') if enrollment else '',
            'reviewed_at': getattr(enrollment, 'reviewed_at', None) if enrollment else None,
            'owner': _user_brief(s.owner),
        })

    return Response({'results': rows})


@api_view(['POST'])
@permission_classes([IsAdminRole])
def admin_store_decide(request, pk):
    """입점 신청 승인/반려. 매장 pk 기준이다."""
    store = Restaurant.objects.select_related('owner', 'enrollment_request').filter(
        pk=pk, owner__role=User.Role.OWNER,
    ).first()
    if not store:
        return error_response('not_found', '매장을 찾을 수 없습니다.', 404)

    if _store_review_status(store) != 'pending':
        return error_response('already_decided', '이미 처리된 입점 신청입니다.', 409)

    action, reason, err = _decide_action(request)
    if err:
        return err

    owner = store.owner
    with transaction.atomic():
        enrollment_status = 'approved' if action == 'approve' else 'rejected'

        # 이 점주의 '최초' 매장(계정이 아직 pending)인 경우에만 계정 상태도 함께 바꾼다.
        if owner and owner.status == User.Status.PENDING:
            owner.status = User.Status.ACTIVE if action == 'approve' else User.Status.SUSPENDED
            owner.is_active = action == 'approve'
            owner.save(update_fields=['status', 'is_active'])

        defaults = {
            'username': owner.username if owner else '',
            'phone': ((owner.phone if owner else '') or '')[:20],
            'owner_name': (owner.nickname or owner.username) if owner else '',
            'restaurant_name': store.name,
            'status': enrollment_status,
            'reviewed_at': timezone.now(),
            'reviewed_by': request.user,
        }
        if action == 'reject':
            defaults['rejection_reason'] = reason

        EnrollmentRequest.objects.update_or_create(restaurant=store, defaults=defaults)

    detail = (f'{store.name}의 입점을 승인했습니다.' if action == 'approve'
              else f'{store.name}의 입점 신청을 반려했습니다.')
    return Response({'detail': detail, 'review_status': enrollment_status})
