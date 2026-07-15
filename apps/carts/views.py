"""Cart endpoints (/api/v1/cart). Customer only."""

from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from common.exceptions import error_response
from common.permissions import IsCustomer
from restaurants.models import Menu
from .models import Cart, CartItem
from .serializers import CartItemCreateSerializer, CartSerializer
from .services import cart_totals


def _get_cart(user):
    cart, _ = Cart.objects.get_or_create(user=user)
    return cart


@api_view(['GET'])
@permission_classes([IsCustomer])
def cart_detail(request):
    cart = _get_cart(request.user)
    return Response(CartSerializer(cart).data)


@api_view(['POST'])
@permission_classes([IsCustomer])
def add_item(request):
    """장바구니에 음식 추가. quantity>=1 강제, 단일 음식점만 허용한다."""
    serializer = CartItemCreateSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    menu = serializer.validated_data['menu']
    quantity = serializer.validated_data['quantity']
    options = serializer.validated_data['options']

    cart = _get_cart(request.user)

    if quantity < 1:
        return error_response('invalid_quantity', '수량은 1 이상이어야 합니다.', 400)
    if cart.restaurant and cart.restaurant_id != menu.restaurant_id and cart.items.exists():
        return error_response(
            'restaurant_conflict', '다른 음식점의 메뉴는 함께 담을 수 없습니다.', 409,
        )

    if not cart.restaurant_id or not cart.items.exists():
        cart.restaurant_id = menu.restaurant_id
        cart.save(update_fields=['restaurant'])

    item = CartItem.objects.create(
        cart=cart, menu=menu, quantity=quantity, options=options,
    )
    return Response(CartSerializer(cart).data, status=201)


@api_view(['PUT', 'DELETE'])
@permission_classes([IsCustomer])
def item_detail(request, pk):
    """장바구니 항목 수량·옵션 변경 / 삭제. 소유자 검증 후 pk로 항목을 조회한다."""
    item = CartItem.objects.filter(pk=pk, cart__user=request.user).first()

    if not item:
        return error_response('not_found', '장바구니 항목을 찾을 수 없습니다.', 404)

    if request.method == 'DELETE':
        cart = item.cart
        item.delete()
        return Response(CartSerializer(cart).data)

    if 'quantity' in request.data:
        try:
            quantity = int(request.data['quantity'])
        except (TypeError, ValueError):
            return error_response('invalid_quantity', 'quantity는 숫자여야 합니다.', 400)
        if quantity < 1:
            return error_response('invalid_quantity', '수량은 1 이상이어야 합니다.', 400)
        item.quantity = quantity
    if 'options' in request.data:
        item.options = request.data['options']
    item.save()
    return Response(CartSerializer(item.cart).data)


@api_view(['POST'])
@permission_classes([IsCustomer])
def apply_coupon(request):
    """쿠폰 적용. 사용자 보유 쿠폰 + 활성/유효기간을 확인한다."""
    from promotions.models import Coupon, UserCoupon

    code = request.data.get('code')
    if not code:
        return error_response('bad_request', '쿠폰 코드가 필요합니다.', 400)

    cart = _get_cart(request.user)

    coupon = Coupon.objects.filter(code=code, is_active=True).first()
    if not coupon:
        return error_response('coupon_not_found', '사용할 수 없는 쿠폰입니다.', 404)
    owned = UserCoupon.objects.filter(user=request.user, coupon=coupon, is_used=False).exists()
    if not owned:
        return error_response('coupon_not_owned', '보유하지 않은 쿠폰입니다.', 403)
    totals = cart_totals(cart)
    if totals['subtotal'] < coupon.min_order_amount:
        return error_response(
            'min_order_not_met',
            f'최소 주문금액 {coupon.min_order_amount}원을 충족해야 합니다.', 400,
        )
    cart.coupon = coupon
    cart.save(update_fields=['coupon'])
    return Response(CartSerializer(cart).data)


@api_view(['GET'])
@permission_classes([IsCustomer])
def cart_summary(request):
    cart = _get_cart(request.user)
    return Response(cart_totals(cart))
