"""Rider delivery endpoints (/api/v1/rider/deliveries). Require rider role."""

from django.db.models import Q
from django.utils import timezone
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from common.exceptions import error_response
from common.permissions import IsRider
from orders.models import Order
from promotions.services import award_order_points
from restaurants.models import Menu
from .models import Delivery, RiderLocation
from .serializers import (
    DeliveryDetailSerializer,
    DeliveryListSerializer,
    RiderLocationSerializer,
    RiderMenuSerializer,
)

_STATUS_TO_ORDER = {
    Delivery.Status.DELIVERED: Order.Status.DELIVERED,
    Delivery.Status.DELIVERING: Order.Status.DELIVERING,
}


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
