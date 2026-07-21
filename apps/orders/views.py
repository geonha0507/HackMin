"""Order endpoints (/api/v1/orders) and /api/v1/me/orders."""

from datetime import date

from django.db import transaction
from rest_framework import generics
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from carts.models import Cart
from carts.services import cart_totals, compute_line_total, compute_unit_price
from common.exceptions import error_response
from common.permissions import IsCustomer
from .models import Order, OrderItem
from payments.models import Payment
from restaurants.models import Menu
from .serializers import OrderCreateSerializer, OrderSerializer

# 취소 가능한 주문 상태(보안 모드 기준).
_CANCELLABLE = {Order.Status.PENDING, Order.Status.PLACED, Order.Status.ACCEPTED}


@api_view(['POST'])
@permission_classes([IsCustomer])
def create_order(request):
    """주문 생성. 장바구니로부터 서버에서 금액을 재계산한다.
    
    수정: 장바구니 비우기를 create_order에서 제거 → create_payment에서 결제 성공 시에만 처리.
    """
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
            coupon=cart.coupon,  # Order에 쿠폰 저장
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
        # 주문 생성 후 장바구니 비우기는 하지 않음 → 결제 성공 후에만 처리

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
    """주문 취소. 본인 주문 + 취소 가능 상태만 허용한다.
    
    수정: #3 결제된 건이 있으면 함께 취소 처리.
    """
    order = Order.objects.filter(pk=pk, user=request.user).first()
    if not order:
        return error_response('not_found', '주문을 찾을 수 없습니다.', 404)
    if order.status not in _CANCELLABLE:
        return error_response('not_cancellable', '현재 상태에서는 취소할 수 없습니다.', 409)
    
    with transaction.atomic():
        order.status = Order.Status.CANCELLED
        order.save(update_fields=['status'])
        
        # #3: 결제된 건이 있으면 함께 취소 (cancel_payment와 동일 동작)
        for p in order.payments.filter(status=Payment.Status.PAID):
            p.status = Payment.Status.CANCELLED
            p.save(update_fields=['status'])
        
        # 쿠폰 복구 (취소 시 사용 처리를 되돌림)
        # is_used/used_at 는 Coupon 이 아니라 UserCoupon(사용자-쿠폰 매핑)의 필드다.
        if order.coupon:
            from promotions.models import UserCoupon
            UserCoupon.objects.filter(user=order.user, coupon=order.coupon).update(
                is_used=False, used_at=None,
            )

        # 장바구니 복구
        cart, _ = Cart.objects.get_or_create(user=request.user)
        cart.restaurant = order.restaurant
        if order.coupon:
            cart.coupon = order.coupon
        cart.save()
        
        # 주문 아이템들을 장바구니에 복구
        from carts.models import CartItem
        for item in order.items.all():
            if item.menu_id:
                CartItem.objects.create(
                    cart=cart,
                    menu_id=item.menu_id,
                    quantity=item.quantity,
                    options=item.options,
                )
    
    return Response(OrderSerializer(order).data)


@api_view(['POST'])
@permission_classes([IsCustomer])
def reorder(request, pk):
    """동일 메뉴 재주문: 이전 주문 항목을 장바구니에 담는다.
    
    수정: #7 판매중단 메뉴는 제외하고, 판매중(ON_SALE) 메뉴만 담기.
    """
    order = Order.objects.filter(pk=pk, user=request.user).first()
    if not order:
        return error_response('not_found', '주문을 찾을 수 없습니다.', 404)
    
    cart, _ = Cart.objects.get_or_create(user=request.user)
    cart.items.all().delete()
    cart.restaurant = order.restaurant
    cart.coupon = None
    cart.save()
    
    from carts.models import CartItem
    excluded_count = 0
    
    for item in order.items.all():
        # #7: menu는 SET_NULL이라 None일 수 있음 + 판매중(ON_SALE)만 추가
        if item.menu_id and item.menu and item.menu.status == Menu.Status.ON_SALE:
            CartItem.objects.create(
                cart=cart,
                menu_id=item.menu_id,
                quantity=item.quantity,
                options=item.options,
            )
        else:
            excluded_count += 1
    
    from carts.serializers import CartSerializer
    response_data = CartSerializer(cart).data
    
    # (선택) 걸러진 항목 수를 응답에 포함
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
