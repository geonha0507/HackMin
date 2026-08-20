"""Rider delivery endpoints (/api/v1/rider/deliveries). Require rider role."""

from django.contrib.auth import get_user_model
from django.db.models import Q
from django.utils import timezone
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from common.exceptions import error_response
from common.permissions import IsRider
from orders.models import Order
from promotions.services import award_order_points
from restaurants.models import Menu
from .models import Delivery, RiderLocation, RiderProfile
from .serializers import (
    DeliveryDetailSerializer,
    DeliveryListSerializer,
    RiderLocationSerializer,
    RiderMenuSerializer,
    RiderProfileSerializer,
)

User = get_user_model()

_STATUS_TO_ORDER = {
    Delivery.Status.DELIVERED: Order.Status.DELIVERED,
    Delivery.Status.DELIVERING: Order.Status.DELIVERING,
}

# 거리 기반 배달료 정책: 기본료 + 거리(km) × km당 요금.
FEE_BASE = 3000
FEE_PER_KM = 1000


def compute_fee(distance_km):
    """이동 거리(km)로 배달료를 산정한다. 거리는 앱이 보고한 값을 그대로 신뢰한다."""
    try:
        km = max(0.0, float(distance_km))
    except (TypeError, ValueError):
        km = 0.0
    return FEE_BASE + round(km * FEE_PER_KM)


def _provision_deliveries():
    """배달 요청(delivering) 상태인데 Delivery가 없는 주문에 대해 배차 풀 생성."""
    pending = Order.objects.filter(status=Order.Status.DELIVERING, delivery__isnull=True)
    for order in pending:
        Delivery.objects.get_or_create(order=order)


@api_view(['GET'])
@permission_classes([IsRider])
def delivery_list(request):
    """배달 주문 조회. 본인 배정 건 + 미배정(가용) 건만 조회한다."""
    _provision_deliveries()
    deliveries = Delivery.objects.filter(Q(rider=request.user) | Q(rider__isnull=True))
    deliveries = deliveries.select_related('order', 'order__restaurant').order_by('-assigned_at')
    return Response({'results': DeliveryListSerializer(deliveries, many=True).data})


def _get_delivery(request, pk):
    return Delivery.objects.filter(Q(rider=request.user) | Q(rider__isnull=True), pk=pk).first()


@api_view(['GET'])
@permission_classes([IsRider])
def delivery_detail(request, pk):
    """배달 상세(주소·연락처)."""
    delivery = _get_delivery(request, pk)
    if not delivery:
        return error_response('not_found', '배달 정보를 찾을 수 없습니다.', 404)
    return Response(DeliveryDetailSerializer(delivery).data)


@api_view(['PUT'])
@permission_classes([IsRider])
def delivery_status(request, pk):
    """배달 상태 변경(배달 중/완료). 본인 배정 건만 변경 가능(미배정 건은 수령 시 본인에게 배정)."""
    delivery = _get_delivery(request, pk)
    if not delivery:
        return error_response('not_found', '배달 정보를 찾을 수 없습니다.', 404)

    new_status = request.data.get('status')
    if new_status not in Delivery.Status.values:
        return error_response('bad_request', '유효하지 않은 상태입니다.', 400)

    if delivery.rider is None:
        delivery.rider = request.user

    delivery.status = new_status
    if new_status == Delivery.Status.DELIVERED:
        delivery.completed_at = timezone.now()
        # 앱이 GPS로 계산해 보고한 이동 거리로 배달료를 산정한다.
        # (서버는 보고된 거리를 검증 없이 신뢰 — 거리 기반 정산)
        delivery.distance_km = request.data.get('distance_km', 0) or 0
        delivery.fee = compute_fee(delivery.distance_km)
    delivery.save()

    if new_status in _STATUS_TO_ORDER:
        delivery.order.status = _STATUS_TO_ORDER[new_status]
        delivery.order.save(update_fields=['status'])
        if new_status == Delivery.Status.DELIVERED:
            award_order_points(delivery.order)

    return Response(DeliveryDetailSerializer(delivery).data)


@api_view(['GET'])
@permission_classes([IsRider])
def menus(request):
    """해킹의 민족 전체 메뉴 목록(홈 노출용). 숨김(hidden) 메뉴는 제외하고,
    매장명·가격·사진과 함께 내려준다. 사진 있는 메뉴를 우선 노출한다.

    쿼리: ?limit=N (기본 30). 이미지 URL은 상대경로일 수 있고, 앱이 절대 URL로 만든다.
    """
    try:
        limit = int(request.query_params.get('limit', 30))
    except (TypeError, ValueError):
        limit = 30
    limit = max(1, min(limit, 100))

    qs = (
        Menu.objects.exclude(status=Menu.Status.HIDDEN)
        .select_related('restaurant')
        .order_by('-id')
    )
    # 사진 있는 메뉴를 앞으로(빈 이미지는 뒤로) 정렬 — DB 종류와 무관하게 파이썬에서 처리.
    items = sorted(qs[: limit * 2], key=lambda m: (not bool(m.image), -m.id))[:limit]
    return Response({'results': RiderMenuSerializer(items, many=True).data})


@api_view(['GET', 'PUT'])
@permission_classes([IsRider])
def profile(request):
    """배달 전 정보(정산 계좌·면허·차량·희망지역·배달수단).

    - GET: 내 프로필(없으면 빈 값들).
    - PUT: 부분 수정(upsert). account_number 는 암호화 저장, 응답은 마스킹.
    """
    obj, _ = RiderProfile.objects.get_or_create(rider=request.user)
    if request.method == 'GET':
        return Response(RiderProfileSerializer(obj).data)

    serializer = RiderProfileSerializer(obj, data=request.data, partial=True)
    serializer.is_valid(raise_exception=True)
    serializer.save()
    return Response(RiderProfileSerializer(obj).data)


@api_view(['GET', 'PUT'])
@permission_classes([IsRider])
def location(request):
    """라이더 본인의 실시간 위치.

    - GET: 마지막으로 저장된 내 위치(없으면 204).
    - PUT: {latitude, longitude, accuracy?} 로 내 위치를 갱신(upsert).
      해킹커넥트(라이더 앱)가 운행 중 주기적으로 호출한다.
    """
    if request.method == 'GET':
        loc = RiderLocation.objects.filter(rider=request.user).first()
        if not loc:
            return Response(status=204)
        return Response(RiderLocationSerializer(loc).data)

    serializer = RiderLocationSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    loc, _ = RiderLocation.objects.update_or_create(
        rider=request.user, defaults=serializer.validated_data,
    )
    return Response(RiderLocationSerializer(loc).data)


# ─────────────────────────────────────────────────────────────
#  [의도된 취약점] IDOR / BOLA (Broken Object Level Authorization)
#
#  대조군: 위 profile()·location() 은 대상을 request.user 로 고정한다(정상).
#  아래 두 엔드포인트는 URL 의 rider pk 로 대상 객체를 찾고, 요청자가 그
#  라이더 본인인지 검증하지 않는다. 로그인한 라이더면 누구나 순차 id 를
#  갈아끼워 타 라이더의 정산 정보를 열람(GET)·변조(PUT)할 수 있다.
#  → 공격자가 남의 계좌번호를 자기 것으로 바꿔 정산금을 가로챈다(교육용).
# ─────────────────────────────────────────────────────────────

@api_view(['GET'])
@permission_classes([IsRider])
def rider_profile_by_id(request, pk):
    """라이더 pk 의 프로필 조회. 소유권 검증 없음(IDOR)."""
    obj = RiderProfile.objects.filter(rider_id=pk).select_related('rider').first()
    if not obj:
        return error_response('not_found', '프로필을 찾을 수 없습니다.', 404)
    data = RiderProfileSerializer(obj).data
    # id↔사람 매핑을 쉽게 해 열거(enumeration)를 돕는다 — 취약점 데모용.
    data['rider_id'] = obj.rider_id
    data['nickname'] = getattr(obj.rider, 'nickname', '')
    return Response(data)


@api_view(['PUT'])
@permission_classes([IsRider])
def rider_account_by_id(request, pk):
    """라이더 pk 의 정산 계좌 변경. 소유권 검증 없음(IDOR).

    body: {"account_number": "...", "bank_name"?, "account_holder"?}
    공격자가 타 라이더 계좌를 자기 것으로 바꿔 정산금을 가로챌 수 있다.
    """
    if not User.objects.filter(pk=pk).exists():
        return error_response('not_found', '라이더를 찾을 수 없습니다.', 404)
    obj, _ = RiderProfile.objects.get_or_create(rider_id=pk)
    serializer = RiderProfileSerializer(obj, data=request.data, partial=True)
    serializer.is_valid(raise_exception=True)
    serializer.save()
    return Response(RiderProfileSerializer(obj).data)
