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

    [방어 ⑩] 수령확인은 '네이티브 거래서명'(X-Receipt-*)을 필수로 요구한다. 서명은
    고객앱(해킹의민족) libhackminsec.so 의 signReceipt() 가 Android Keystore(EC P-256)
    개인키로 canonical 을 서명한 값이다. 개인키는 하드웨어(TEE)에 격리돼 앱조차 원시
    바이트를 못 보므로 커스텀 클라이언트(무루팅)는 유효 서명을 오프라인으로 못 만든다 → 401.
    유일한 우회는 고객앱을 루팅해 살아있는 서명 함수를 오라클로 부리는 것(= 온디바이스 후킹).
    canonical = "POST\\n<path>\\n<ts>\\n<nonce>\\n" (본문은 서명 대상에서 제외한다).
    """
    from rider.models import Delivery  # 앱 간 순환 import 회피(지연 로드)
    from common.txnsig import TxnSigError, verify_txn

    ts = request.headers.get('X-Receipt-Ts')
    nonce = request.headers.get('X-Receipt-Nonce')
    sig = request.headers.get('X-Receipt-Sig')
    key_id = request.headers.get('X-Key-Id')
    try:
        # 본문(b'')은 서명 대상 아님(앱 CryptoInterceptor 암호화와 순서 충돌 회피)
        verify_txn(request.user, request.method, request.path,
                   ts, nonce, sig, key_id, b'')
    except TxnSigError as exc:
        return error_response('receipt_sig_required', f'수령확인 서명 검증 실패: {exc}', 401)

    order = Order.objects.filter(pk=pk, user=request.user).first()
    if not order:
        return error_response('not_found', '주문을 찾을 수 없습니다.', 404)
    delivery = getattr(order, 'delivery', None)
    if not delivery or delivery.status != Delivery.Status.DELIVERED:
        return error_response('not_deliverable', '배달완료 상태에서만 수령확인할 수 있습니다.', 409)

    if not delivery.settled or not delivery.receipt_proof:
        delivery.settled = True
        delivery.settled_at = timezone.now()
        # [fix③] 정산 집계가 나중에 재검증할 수 있도록 수령확인 서명 증거를 저장한다.
        delivery.receipt_proof = {
            'ts': ts, 'nonce': nonce, 'sig': sig,
            'key_id': key_id, 'path': request.path,
        }
        delivery.save(update_fields=['settled', 'settled_at', 'receipt_proof'])
    return Response({'order': order.id, 'settled': True, 'fee': delivery.fee})


@api_view(['POST'])
@permission_classes([IsCustomer])
def register_receipt_key(request):
    """[방어 ⑩] 수령확인 거래서명 공개키 등록.

    고객앱이 Android Keystore(EC P-256)에서 개인키를 생성하고 공개키(PEM)만 올린다.
    서버는 이 공개키로 수령확인 서명을 검증한다(개인키는 저장하지 않는다).
    라이더의 TxnKey 와 동일 저장소를 재사용한다(사용자별 key_id → 공개키 매핑).
    """
    from rider.models import TxnKey
    from common.txnsig import reg_seal
    key_id = request.data.get('key_id')
    pem = request.data.get('public_key_pem')
    if not key_id or not pem:
        return error_response('bad_request', 'key_id/public_key_pem 이 필요합니다.', 400)
    # [fix③ 앵커] 등록과 동시에 봉인값을 저장한다(SQLi 공개키 스왑 차단).
    TxnKey.objects.update_or_create(
        user=request.user, key_id=key_id,
        defaults={'public_key_pem': pem,
                  'reg_seal': reg_seal(request.user.id, key_id, pem)})
    return Response({'key_id': key_id}, status=201)


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
