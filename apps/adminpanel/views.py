"""Admin endpoints (/api/v1/admin). Require admin role."""

from django.contrib.auth import get_user_model
from django.db import connection
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from common.exceptions import error_response
from common.mode import is_vulnerable
from common.permissions import IsAdminRole
from orders.models import Order
from orders.serializers import OrderSerializer
from payments.models import Payment
from payments.serializers import PaymentSerializer

User = get_user_model()


def _user_public(user):
    return {
        'id': user.id, 'username': user.username, 'email': user.email,
        'phone': user.phone, 'nickname': user.nickname, 'role': user.role,
        'status': user.status, 'is_active': user.is_active, 'date_joined': user.date_joined,
    }


def _user_full(user):
    """🎯 Vulnerable 모드에서 비밀번호 해시까지 노출."""
    data = _user_public(user)
    data['password_hash'] = user.password
    data['is_staff'] = user.is_staff
    data['is_superuser'] = user.is_superuser
    return data


@api_view(['GET'])
@permission_classes([IsAdminRole])
def user_list(request):
    """🎯 사용자 목록. ?q= 검색.

    Vulnerable 모드: q를 raw SQL에 결합(SQLi) + 비밀번호 해시 노출.
    Secure 모드: ORM 필터 + 민감정보 제외.
    """
    q = request.query_params.get('q', '')
    if is_vulnerable(request):
        if q:
            sql = (
                "SELECT id FROM accounts_user "
                f"WHERE username LIKE '%{q}%' OR email LIKE '%{q}%'"  # noqa
            )
            try:
                with connection.cursor() as cursor:
                    cursor.execute(sql)
                    ids = [r[0] for r in cursor.fetchall()]
            except Exception as exc:
                return error_response('query_error', str(exc), 400)
            users = User.objects.filter(id__in=ids)
        else:
            users = User.objects.all()
        return Response({'results': [_user_full(u) for u in users]})

    users = User.objects.all()
    if q:
        from django.db.models import Q
        users = users.filter(Q(username__icontains=q) | Q(email__icontains=q))
    return Response({'results': [_user_public(u) for u in users]})


@api_view(['GET', 'DELETE'])
@permission_classes([IsAdminRole])
def user_detail(request, pk):
    """🎯 사용자 상세 조회 / 회원 탈퇴 처리.

    조회는 Vulnerable 모드에서 비밀번호 해시 등 민감정보를 노출한다.
    """
    user = User.objects.filter(pk=pk).first()
    if not user:
        return error_response('not_found', '사용자를 찾을 수 없습니다.', 404)
    if request.method == 'DELETE':
        user.status = User.Status.WITHDRAWN
        user.is_active = False
        user.save(update_fields=['status', 'is_active'])
        return Response(status=204)
    if is_vulnerable(request):
        return Response(_user_full(user))
    return Response(_user_public(user))


@api_view(['PUT'])
@permission_classes([IsAdminRole])
def user_status(request, pk):
    user = User.objects.filter(pk=pk).first()
    if not user:
        return error_response('not_found', '사용자를 찾을 수 없습니다.', 404)
    new_status = request.data.get('status')
    if new_status not in User.Status.values:
        return error_response('bad_request', '유효하지 않은 상태입니다.', 400)
    user.status = new_status
    user.is_active = new_status == User.Status.ACTIVE
    user.save(update_fields=['status', 'is_active'])
    return Response(_user_public(user))


@api_view(['GET'])
@permission_classes([IsAdminRole])
def owner_list(request):
    owners = User.objects.filter(role=User.Role.OWNER)
    return Response({'results': [_user_public(u) for u in owners]})


@api_view(['PUT'])
@permission_classes([IsAdminRole])
def owner_status(request, pk):
    owner = User.objects.filter(pk=pk, role=User.Role.OWNER).first()
    if not owner:
        return error_response('not_found', '점주를 찾을 수 없습니다.', 404)
    is_active = bool(request.data.get('is_active', True))
    owner.is_active = is_active
    owner.status = User.Status.ACTIVE if is_active else User.Status.SUSPENDED
    owner.save(update_fields=['is_active', 'status'])
    return Response(_user_public(owner))


@api_view(['GET'])
@permission_classes([IsAdminRole])
def order_list(request):
    orders = Order.objects.all().order_by('-created_at')
    return Response({'results': OrderSerializer(orders, many=True).data})


@api_view(['GET'])
@permission_classes([IsAdminRole])
def payment_list(request):
    payments = Payment.objects.all().order_by('-created_at')
    return Response({'results': PaymentSerializer(payments, many=True).data})
