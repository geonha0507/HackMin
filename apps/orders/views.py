"""Order endpoints (/api/v1/orders) and /api/v1/me/orders.

뷰는 입력 검증·권한·에러 응답 매핑만 담당하고, 주문 도메인 로직은 orders.services 에 있다.
"""

from datetime import date

from django.utils import timezone
from rest_framework import generics
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from carts.models import Cart
from carts.serializers import CartSerializer
from carts.services import cart_totals
from common.exceptions import error_response
from common.permissions import IsCustomer

from . import services
from .models import Order
from .serializers import OrderCreateSerializer, OrderSerializer


@api_view(['POST'])
@permission_classes([IsCustomer])
def create_order(request):
    """주문 생성. 장바구니로부터 서버에서 금액을 재계산한다."""
    serializer = OrderCreateSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    data = serializer.validated_data

    cart = Cart.objects.filter(user=request.user).first()
    if not cart or not cart.items.exists():
        return error_response('empty_cart', '장바구니가 비어 있습니다.', 400)

    if cart.restaurant:
        today = date.today()
        is_closed = (
            cart.restaurant.regular_closed_days.filter(weekday=today.weekday()).exists()
            or cart.restaurant.closed_dates.filter(date=today).exists()
        )
        if is_closed:
            return error_response('restaurant_closed', '오늘은 휴무일이라 주문할 수 없습니다.', 400)

    totals = cart_totals(cart)

    # 최소 주문 금액 검증: 상품 합계(subtotal)가 매장 최소주문 미만이면 주문 불가.
    if cart.restaurant and totals['subtotal'] < cart.restaurant.min_order_amount:
        return error_response(
            'min_order_not_met',
            f'최소 주문금액은 {cart.restaurant.min_order_amount}원입니다.', 400,
        )

    order = services.create_order_from_cart(
        request.user, cart, totals,
        address=data['address'],
        address_detail=data['address_detail'],
        request_note=data['request_note'],
    )
    return Response(OrderSerializer(order).data, status=201)


@api_view(['GET'])
@permission_classes([IsCustomer])
def order_detail(request, pk):
    """주문 상세 조회. 소유자 검증 후 조회한다."""
    order = Order.objects.filter(pk=pk, user=request.user).first()
    if not order:
        return error_response('not_found', '주문을 찾을 수 없습니다.', 404)
    return Response(OrderSerializer(order).data)


@api_view(['GET'])
@permission_classes([IsCustomer])
def order_status(request, pk):
    order = Order.objects.filter(pk=pk, user=request.user).first()
    if not order:
        return error_response('not_found', '주문을 찾을 수 없습니다.', 404)
    return Response({'id': order.id, 'order_number': order.order_number, 'status': order.status})


@api_view(['POST'])
@permission_classes([IsCustomer])
def confirm_receipt(request, pk):
    """고객 수령확인. 배달이 '배달완료'된 뒤 주문한 고객이 이 확인을 해야
    라이더 배달료가 '정산 확정(settled)'된다. 확인 전에는 지급 대기 상태다.

    소유자(주문한 고객)만, 배달완료 상태에서만 가능. 이미 확정됐으면 그대로 둔다.
    """
    from rider.models import Delivery  # 앱 간 순환 import 회피(지연 로드)

    order = Order.objects.filter(pk=pk, user=request.user).first()
    if not order:
        return error_response('not_found', '주문을 찾을 수 없습니다.', 404)
    delivery = getattr(order, 'delivery', None)
    if not delivery or delivery.status != Delivery.Status.DELIVERED:
        return error_response('not_deliverable', '배달완료 상태에서만 수령확인할 수 있습니다.', 409)

    if not delivery.settled:
        delivery.settled = True
        delivery.settled_at = timezone.now()
        delivery.save(update_fields=['settled', 'settled_at'])
    return Response({'order': order.id, 'settled': True, 'fee': delivery.fee})


@api_view(['POST'])
@permission_classes([IsCustomer])
def cancel_order(request, pk):
    """주문 취소. 본인 주문 + 취소 가능 상태만 허용한다."""
    order = Order.objects.filter(pk=pk, user=request.user).first()
    if not order:
        return error_response('not_found', '주문을 찾을 수 없습니다.', 404)
    if order.status not in services.CANCELLABLE_STATUSES:
        return error_response('not_cancellable', '현재 상태에서는 취소할 수 없습니다.', 409)

    services.cancel_order(order)
    return Response(OrderSerializer(order).data)


@api_view(['POST'])
@permission_classes([IsCustomer])
def reorder(request, pk):
    """동일 메뉴 재주문: 이전 주문 항목(판매중)을 장바구니에 담는다."""
    order = Order.objects.filter(pk=pk, user=request.user).first()
    if not order:
        return error_response('not_found', '주문을 찾을 수 없습니다.', 404)

    cart, excluded_count = services.reorder_to_cart(request.user, order)
    response_data = CartSerializer(cart).data
    if excluded_count > 0:
        response_data['excluded_items_count'] = excluded_count
        response_data['excluded_message'] = f'{excluded_count}개의 판매중단 메뉴는 제외되었습니다.'
    return Response(response_data, status=201)


class MyOrderListView(generics.ListAPIView):
    """/me/orders 주문 내역."""
    serializer_class = OrderSerializer
    permission_classes = [IsCustomer]

    def get_queryset(self):
        return Order.objects.filter(user=self.request.user)
