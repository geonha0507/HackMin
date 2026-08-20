"""Rider delivery endpoints (/api/v1/rider/deliveries). Require rider role."""

import math

from django.db.models import Q, Sum
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

_STATUS_TO_ORDER = {
    Delivery.Status.DELIVERED: Order.Status.DELIVERED,
    Delivery.Status.DELIVERING: Order.Status.DELIVERING,
}

# 거리 기반 배달료 정책: 기본료 + 거리(km) × km당 요금.
FEE_BASE = 3000
FEE_PER_KM = 1000


def compute_fee(distance_km):
    """이동 거리(km)로 배달료를 산정한다."""
    try:
        km = max(0.0, float(distance_km))
    except (TypeError, ValueError):
        km = 0.0
    return FEE_BASE + round(km * FEE_PER_KM)


# 위치 무결성: 단일 위치 갱신이 이 거리(km)를 초과해 점프하면 순간이동으로 보고 거부한다.
# 실시간 추적 앱은 수 초 간격으로 갱신하므로 한 번에 2km를 넘을 수 없다.
# → 커스텀 클라이언트로 한 번에 먼 좌표를 찍는 방식이 막히고, 거리를 부풀리려면
#   '연속된 짧은 좌표들'을 계속 보내야 한다(= 앱 내 위치 제공자 후킹이 필요).
MAX_SEGMENT_KM = 2.0


def _haversine_km(lat1, lon1, lat2, lon2):
    """두 좌표 사이 대원거리(km)."""
    radius = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlmb = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlmb / 2) ** 2
    return 2 * radius * math.asin(math.sqrt(a))


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
    if new_status == Delivery.Status.DELIVERING:
        # 배달 시작 시점의 누적 이동거리를 스냅샷 — 완료까지의 증가분이 '이 배달의
        # 실제 이동거리'가 된다.
        loc = RiderLocation.objects.filter(rider=request.user).first()
        delivery.start_distance_km = loc.total_distance_km if loc else 0.0
    if new_status == Delivery.Status.DELIVERED:
        delivery.completed_at = timezone.now()
        # 클라가 보고한 distance_km 는 신뢰하지 않는다. 서버가 위치 트랙으로 관측한
        # 실제 이동거리(속도상한을 통과한 연속 좌표들의 누적 증가분)로 산정한다.
        loc = RiderLocation.objects.filter(rider=request.user).first()
        observed = (loc.total_distance_km - delivery.start_distance_km) if loc else 0.0
        delivery.distance_km = max(0.0, round(observed, 3))
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
    data = serializer.validated_data

    # 직전 위치와 비교해 이동속도를 검증하고, 통과한 구간거리만 누적한다.
    prev = RiderLocation.objects.filter(rider=request.user).first()
    total = 0.0
    if prev:
        seg = _haversine_km(prev.latitude, prev.longitude,
                            data['latitude'], data['longitude'])
        if seg > MAX_SEGMENT_KM:
            return error_response(
                'implausible_move',
                f'위치가 한 번에 너무 멀리 이동했습니다({seg:.1f}km). 순간이동은 허용되지 않습니다.',
                400)
        total = prev.total_distance_km + seg

    defaults = dict(data)
    defaults['total_distance_km'] = total
    loc, _ = RiderLocation.objects.update_or_create(
        rider=request.user, defaults=defaults,
    )
    return Response(RiderLocationSerializer(loc).data)


@api_view(['GET'])
@permission_classes([IsRider])
def earnings(request):
    """내 배달비 정산 현황.

    배달완료(delivered) 건의 배달료를 두 갈래로 집계한다:
      - settled_total : 고객 수령확인까지 끝나 '지급 확정'된 금액
      - pending_total : 배달은 끝났으나 고객 수령확인 대기 중(=아직 못 받는 돈)
    즉 고객이 수령확인을 해야만 배달료가 settled_total 로 넘어간다.
    """
    qs = Delivery.objects.filter(rider=request.user, status=Delivery.Status.DELIVERED)
    settled = qs.filter(settled=True).aggregate(s=Sum('fee'))['s'] or 0
    pending = qs.filter(settled=False).aggregate(s=Sum('fee'))['s'] or 0
    return Response({'settled_total': settled, 'pending_total': pending})
