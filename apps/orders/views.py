"""Order endpoints (/api/v1/orders) and /api/v1/me/orders."""

from django.db import transaction
from rest_framework import generics
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from carts.models import Cart
from carts.services import cart_totals, compute_line_total, compute_unit_price
from common.exceptions import error_response
from common.permissions import IsCustomer
from .models import Order, OrderItem
from .serializers import OrderCreateSerializer, OrderSerializer

# 취소 가능한 주문 상태(보안 모드 기준).
_CANCELLABLE = {Order.Status.PENDING, Order.Status.PLACED, Order.Status.ACCEPTED}


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

    totals = cart_totals(cart)

    subtotal = totals['subtotal']
    delivery_fee = totals['delivery_fee']
    discount = totals['discount']
    total = totals['total']

    with transaction.atomic():
        order = Order.objects.create(
            user=request.user,
            restaurant=cart.restaurant,
            status=Order.Status.PENDING,
            subtotal=subtotal,
            delivery_fee=delivery_fee,
            discount=discount,
            total=total,
            address=data['address'],
            address_detail=data['address_detail'],
            request_note=data['request_note'],
        )
        for item in cart.items.select_related('menu'):
            OrderItem.objects.create(
                order=order,
                menu=item.menu,
                menu_name=item.menu.name,
                unit_price=compute_unit_price(item.menu, item.options),
                quantity=item.quantity,
                options=item.options,
                line_total=compute_line_total(item.menu, item.options, item.quantity),
            )
        # 주문 후 장바구니 비우기.
        cart.items.all().delete()
        cart.coupon = None
        cart.restaurant = None
        cart.save(update_fields=['coupon', 'restaurant'])

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
def cancel_order(request, pk):
    """주문 취소. 본인 주문 + 취소 가능 상태만 허용한다."""
    order = Order.objects.filter(pk=pk, user=request.user).first()
    if not order:
        return error_response('not_found', '주문을 찾을 수 없습니다.', 404)
    if order.status not in _CANCELLABLE:
        return error_response('not_cancellable', '현재 상태에서는 취소할 수 없습니다.', 409)
    order.status = Order.Status.CANCELLED
    order.save(update_fields=['status'])
    return Response(OrderSerializer(order).data)


@api_view(['POST'])
@permission_classes([IsCustomer])
def reorder(request, pk):
    """동일 메뉴 재주문: 이전 주문 항목을 장바구니에 담는다."""
    order = Order.objects.filter(pk=pk, user=request.user).first()
    if not order:
        return error_response('not_found', '주문을 찾을 수 없습니다.', 404)
    cart, _ = Cart.objects.get_or_create(user=request.user)
    cart.items.all().delete()
    cart.restaurant = order.restaurant
    cart.coupon = None
    cart.save()
    from carts.models import CartItem
    for item in order.items.all():
        if item.menu_id:
            CartItem.objects.create(
                cart=cart, menu_id=item.menu_id, quantity=item.quantity, options=item.options,
            )
    from carts.serializers import CartSerializer
    return Response(CartSerializer(cart).data, status=201)


class MyOrderListView(generics.ListAPIView):
    """/me/orders 주문 내역."""
    serializer_class = OrderSerializer
    permission_classes = [IsCustomer]

    def get_queryset(self):
        return Order.objects.filter(user=self.request.user)
