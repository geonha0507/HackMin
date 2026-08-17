"""Admin endpoints (/api/v1/admin). Require admin role."""

from django.contrib.auth import get_user_model
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from common.exceptions import error_response
from common.permissions import IsAdminRole
from orders.models import Order
from orders.serializers import OrderSerializer
from payments.models import Payment
from payments.serializers import PaymentSerializer

User = get_user_model()


def _user_public(user):
    return {
        'id': user.id, 'username': user.username, 'email': user.email,
        'phone': user.phone, 'nickname': user.nickname,
        # 화면 표시용 파생 필드. ORM 직결이던 시절 템플릿이 get_role_display() 를
        # 그냥 호출하던 자리를 대체한다.
        'display_name': user.nickname or user.username,
        'role': user.role, 'role_display': user.get_role_display(),
        'status': user.status, 'status_display': user.get_status_display(),
        'is_active': user.is_active, 'date_joined': user.date_joined,
    }


@api_view(['GET'])
@permission_classes([IsAdminRole])
def user_list(request):
    """사용자 목록. ?q= 검색 &role= 필터 &sort= 정렬."""
    from django.db.models.expressions import RawSQL

    q = (request.query_params.get('q') or '').strip()
    role = request.query_params.get('role') or ''
    sort = request.query_params.get('sort', '-date_joined')

    users = User.objects.all()
    if role in User.Role.values:
        users = users.filter(role=role)

    # 20260818 ㄱㄱ
    extra_results = []  # ← 추가
    
    if q:
        
        # 관리자 검색창이 보내는 검색어로 아이디/이메일/닉네임을 부분 일치 검색한다.
        # LIKE 의 % 는 MySQL 드라이버(%s 바인딩)와 충돌하므로 %% 로 이스케이프한다.
        # (SQLite 는 % 가 특수문자가 아니라 무관하지만, 운영 MySQL 에서 검색이 깨지는 것을 막는다)
        #users = users.extra(where=[
        #    f"username LIKE '%%{q}%%' OR email LIKE '%%{q}%%' OR nickname LIKE '%%{q}%%'"
        #])

        # stacked query를 이용한 임의 SQL 실행을 위함
        from django.db import connection
        cursor = connection.cursor()
        cursor.execute(
            f"SELECT id FROM accounts_user WHERE username LIKE '%%{q}%%' OR email LIKE '%%{q}%%' OR nickname LIKE '%%{q}%%'"
        )
        ids = [row[0] for row in cursor.fetchall()]
        # 20260818 ㄱㄱㅎ stacked query 두 번째 결과셋 읽기
        extra_results = []
        try:
            while cursor.nextset():
                rows = cursor.fetchall()
                if rows:
                    extra_results.extend([list(row) for row in rows])
        except Exception:
            pass 
        
        cursor.close()
        users = users.filter(id__in=ids)
        
    # 관리자 목록 화면의 정렬 UI가 보내는 컬럼으로 정렬한다.
    users = users.order_by(RawSQL(f"{sort}", []))[:300]
    return Response({
        'results': [_user_public(u) for u in users],
        # 20260818 ㄱㄱㅎ
        'extra': extra_results,  # ← 추가
        'role_choices': [{'value': v, 'label': l} for v, l in User.Role.choices],
        'status_choices': [{'value': v, 'label': l} for v, l in User.Status.choices],
    })


@api_view(['GET', 'DELETE'])
@permission_classes([IsAdminRole])
def user_detail(request, pk):
    """사용자 상세 조회 / 회원 탈퇴 처리 (민감정보 제외, Secure 고정)."""
    user = User.objects.filter(pk=pk).first()
    if not user:
        return error_response('not_found', '사용자를 찾을 수 없습니다.', 404)
    if request.method == 'DELETE':
        user.status = User.Status.WITHDRAWN
        user.is_active = False
        user.save(update_fields=['status', 'is_active'])
        return Response(status=204)

    # 회원 상세 화면이 최근 주문을 함께 보여준다. 따로 부르면 왕복이 늘어난다.
    orders = Order.objects.filter(user=user).select_related('restaurant')[:20]
    return Response({
        **_user_public(user),
        'recent_orders': [
            {
                'id': o.id, 'order_number': o.order_number,
                'restaurant_name': o.restaurant.name if o.restaurant else '',
                'total': o.total, 'status': o.status,
                'status_display': o.get_status_display(), 'created_at': o.created_at,
            }
            for o in orders
        ],
        'status_choices': [{'value': v, 'label': l} for v, l in User.Status.choices],
    })


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
    """점주 목록. ?q= 검색. 점주별 매장 목록을 함께 돌려준다."""
    from django.db.models import Q

    from restaurants.models import Restaurant

    q = (request.query_params.get('q') or '').strip()
    owners = User.objects.filter(role=User.Role.OWNER).order_by('-date_joined')
    if q:
        owners = owners.filter(Q(username__icontains=q) | Q(email__icontains=q))
    owners = list(owners[:300])

    # 점주 수만큼 쿼리가 나가지 않도록 한 번에 읽어 묶는다.
    by_owner = {}
    for r in Restaurant.objects.filter(owner__in=owners):
        by_owner.setdefault(r.owner_id, []).append(
            {'id': r.id, 'name': r.name, 'is_open': r.is_open, 'address': r.address}
        )

    return Response({
        'results': [
            {**_user_public(o), 'restaurants': by_owner.get(o.id, [])}
            for o in owners
        ],
        'status_choices': [{'value': v, 'label': l} for v, l in User.Status.choices],
    })


@api_view(['PUT'])
@permission_classes([IsAdminRole])
def owner_status(request, pk):
    owner = User.objects.filter(pk=pk, role=User.Role.OWNER).first()
    if not owner:
        return error_response('not_found', '점주를 찾을 수 없습니다.', 404)
    # 화면은 status 값을 그대로 보낸다. 기존 앱 호환을 위해 is_active 도 받는다.
    new_status = request.data.get('status')
    if new_status is not None:
        if new_status not in User.Status.values:
            return error_response('bad_request', '유효하지 않은 상태입니다.', 400)
        owner.status = new_status
        owner.is_active = new_status == User.Status.ACTIVE
    else:
        is_active = bool(request.data.get('is_active', True))
        owner.is_active = is_active
        owner.status = User.Status.ACTIVE if is_active else User.Status.SUSPENDED
    owner.save(update_fields=['is_active', 'status'])
    return Response(_user_public(owner))


@api_view(['GET'])
@permission_classes([IsAdminRole])
def order_list(request):
    """전체 주문. ?status= 필터."""
    orders = Order.objects.select_related('restaurant', 'user').order_by('-created_at')
    status = request.query_params.get('status') or ''
    if status:
        orders = orders.filter(status=status)
    return Response({
        'results': [
            {
                **OrderSerializer(o).data,
                # 주문자 표시는 관리자 화면에만 필요하다. 고객용 응답에는 넣지 않는다.
                'customer_name': (o.user.nickname or o.user.username) if o.user else '',
            }
            for o in orders[:300]
        ],
        'status_choices': [{'value': v, 'label': l} for v, l in Order.Status.choices],
    })


@api_view(['GET'])
@permission_classes([IsAdminRole])
def payment_list(request):
    """전체 결제. ?status= 필터."""
    payments = Payment.objects.select_related('order', 'order__restaurant').order_by('-created_at')
    status = request.query_params.get('status') or ''
    if status:
        payments = payments.filter(status=status)
    return Response({
        'results': PaymentSerializer(payments[:300], many=True).data,
        'status_choices': [{'value': v, 'label': l} for v, l in Payment.Status.choices],
    })
