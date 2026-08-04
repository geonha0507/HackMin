"""주문 도메인 서비스.

뷰(HTTP 계층)에서 분리한 주문 생성/취소/재주문 비즈니스 로직. 뷰는 입력 검증과
에러 응답 매핑만 담당하고, 실제 DB 트랜잭션 로직은 여기에 둔다.
"""

import logging

from asgiref.sync import async_to_sync
from channels.layers import get_channel_layer
from django.db import transaction

logger = logging.getLogger(__name__)

from carts.models import Cart, CartItem
from carts.services import compute_line_total, compute_unit_price
from payments.models import Payment
from promotions.models import UserCoupon
from restaurants.models import Menu

from .models import Order, OrderItem

# 취소 가능한 주문 상태.
CANCELLABLE_STATUSES = {
    Order.Status.PENDING,
    Order.Status.PLACED,
    Order.Status.ACCEPTED,
}


@transaction.atomic
def create_order_from_cart(user, cart, totals, *, address, address_detail, request_note):
    """검증이 끝난 장바구니로부터 주문과 주문항목을 생성한다.

    장바구니 비우기는 하지 않는다(결제 성공 시점에만 비운다).
    """
    order = Order.objects.create(
        user=user,
        restaurant=cart.restaurant,
        status=Order.Status.PENDING,
        subtotal=totals['subtotal'],
        delivery_fee=totals['delivery_fee'],
        discount=totals['discount'],
        total=totals['total'],
        coupon=cart.coupon,
        address=address,
        address_detail=address_detail,
        request_note=request_note,
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
    return order


@transaction.atomic
def cancel_order(order):
    """주문을 취소하고 결제/쿠폰/장바구니를 되돌린다.

    호출 전에 취소 가능 상태(CANCELLABLE_STATUSES)인지 검증돼 있어야 한다.
    """
    order.status = Order.Status.CANCELLED
    order.save(update_fields=['status'])

    # WebSocket broadcast: 점주에게 실시간 취소 알림
    _broadcast_status(order)

    # 결제된 건이 있으면 함께 취소.
    for payment in order.payments.filter(status=Payment.Status.PAID):
        payment.status = Payment.Status.CANCELLED
        payment.save(update_fields=['status'])

    # 쿠폰 사용 처리 되돌리기(is_used/used_at 는 UserCoupon 필드).
    if order.coupon:
        UserCoupon.objects.filter(user=order.user, coupon=order.coupon).update(
            is_used=False, used_at=None,
        )

    # 장바구니 복구.
    cart, _ = Cart.objects.get_or_create(user=order.user)
    cart.restaurant = order.restaurant
    if order.coupon:
        cart.coupon = order.coupon
    cart.save()

    for item in order.items.all():
        if item.menu_id:
            CartItem.objects.create(
                cart=cart,
                menu_id=item.menu_id,
                quantity=item.quantity,
                options=item.options,
            )
    return order


def reorder_to_cart(user, order):
    """이전 주문 항목 중 판매중(ON_SALE)인 것만 장바구니에 다시 담는다.

    반환: (cart, excluded_count) — excluded_count 는 판매중단 등으로 제외된 항목 수.
    """
    cart, _ = Cart.objects.get_or_create(user=user)
    cart.items.all().delete()
    cart.restaurant = order.restaurant
    cart.coupon = None
    cart.save()

    excluded_count = 0
    for item in order.items.all():
        # menu 는 SET_NULL 이라 None 일 수 있음 + 판매중(ON_SALE)만 추가.
        if item.menu_id and item.menu and item.menu.status == Menu.Status.ON_SALE:
            CartItem.objects.create(
                cart=cart,
                menu_id=item.menu_id,
                quantity=item.quantity,
                options=item.options,
            )
        else:
            excluded_count += 1
    return cart, excluded_count


def _broadcast_status(order):
    """주문 상태 변경을 WebSocket 으로 broadcast 한다."""
    try:
        channel_layer = get_channel_layer()
        async_to_sync(channel_layer.group_send)(
            f'order_{order.pk}',
            {
                'type': 'order_status_update',
                'order_id': order.pk,
                'status': order.status,
                'status_display': order.get_status_display(),
            },
        )
    except Exception:
        logger.exception('WebSocket broadcast failed for order %s', order.pk)
