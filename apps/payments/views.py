"""Mock payment endpoints (/api/v1/payments). Customer only."""

from django.db import transaction
from django.utils import timezone
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from carts.models import Cart
from common.exceptions import error_response
from common.permissions import IsCustomer
from orders.models import Order
from .models import Payment, Refund
from .serializers import PaymentCreateSerializer, PaymentSerializer, RefundSerializer


@api_view(['POST'])
@permission_classes([IsCustomer])
def create_payment(request):
    """결제 생성(모의). 본인 주문 + 결제 금액이 주문 총액과 일치해야 한다.
    
    수정: #1+#2 결제 성공 후 장바구니 비우기 + 쿠폰 사용 처리.
    """
    serializer = PaymentCreateSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    data = serializer.validated_data

    order = Order.objects.filter(pk=data['order'], user=request.user).first()
    if not order:
        return error_response('not_found', '주문을 찾을 수 없습니다.', 404)
    if order.status != Order.Status.PENDING:
        return error_response('invalid_order_state', '결제 가능한 상태가 아닙니다.', 409)
    amount = data.get('amount', order.total)
    if amount != order.total:
        return error_response('amount_mismatch', '결제 금액이 주문 금액과 일치하지 않습니다.', 400)

    with transaction.atomic():
        payment = Payment.objects.create(
            order=order, method=data['method'], amount=amount, status=Payment.Status.PAID,
        )
        order.status = Order.Status.PLACED
        order.save(update_fields=['status'])
        
        # #1+#2: 결제 성공 후에만 장바구니 정리 및 쿠폰 사용 처리
        cart = Cart.objects.filter(user=request.user).first()
        if cart:
            # #2: 적용된 쿠폰을 '사용됨'으로 처리
            if cart.coupon:
                from promotions.models import UserCoupon
                UserCoupon.objects.filter(
                    user=request.user,
                    coupon=cart.coupon,
                    is_used=False,
                ).update(is_used=True, used_at=timezone.now())
            
            # #1: 결제 성공 후에만 장바구니 비우기
            cart.items.all().delete()
            cart.coupon = None
            cart.restaurant = None
            cart.save(update_fields=['coupon', 'restaurant'])
    
    return Response(PaymentSerializer(payment).data, status=201)


def _get_payment(request, pk):
    return Payment.objects.filter(pk=pk, order__user=request.user).first()


@api_view(['GET'])
@permission_classes([IsCustomer])
def payment_detail(request, pk):
    payment = Payment.objects.filter(pk=pk, order__user=request.user).first()
    if not payment:
        return error_response('not_found', '결제 내역을 찾을 수 없습니다.', 404)
    return Response(PaymentSerializer(payment).data)


@api_view(['POST'])
@permission_classes([IsCustomer])
def cancel_payment(request, pk):
    """결제 취소. 소유자 및 상태를 검증한다."""
    payment = _get_payment(request, pk)
    if not payment:
        return error_response('not_found', '결제 내역을 찾을 수 없습니다.', 404)
    if payment.status != Payment.Status.PAID:
        return error_response('not_cancellable', '취소할 수 없는 결제 상태입니다.', 409)
    
    with transaction.atomic():
        payment.status = Payment.Status.CANCELLED
        payment.save(update_fields=['status'])
        payment.order.status = Order.Status.CANCELLED
        payment.order.save(update_fields=['status'])
        
        # 쿠폰 복구 (취소 시 사용 처리를 되돌림)
        # is_used/used_at 는 Coupon 이 아니라 UserCoupon 의 필드다.
        if payment.order.coupon:
            from promotions.models import UserCoupon
            UserCoupon.objects.filter(
                user=payment.order.user, coupon=payment.order.coupon,
            ).update(is_used=False, used_at=None)
    
    return Response(PaymentSerializer(payment).data)


@api_view(['POST'])
@permission_classes([IsCustomer])
def refund_payment(request, pk):
    """환불 요청. 본인 결제 + 남은 환불 가능 금액 이내만 허용한다."""
    payment = _get_payment(request, pk)
    if not payment:
        return error_response('not_found', '결제 내역을 찾을 수 없습니다.', 404)

    try:
        amount = int(request.data.get('amount', payment.amount))
    except (TypeError, ValueError):
        return error_response('bad_request', '유효한 환불 금액이 아닙니다.', 400)
    reason = request.data.get('reason', '')

    already = sum(
        r.amount for r in payment.refunds.filter(status=Refund.Status.COMPLETED)
    )
    remaining = payment.amount - already
    if amount <= 0 or amount > remaining:
        return error_response('invalid_amount', f'환불 가능 금액은 {remaining}원 입니다.', 400)

    refund = Refund.objects.create(
        payment=payment, amount=amount, reason=reason, status=Refund.Status.COMPLETED,
    )
    total_refunded = sum(
        r.amount for r in payment.refunds.filter(status=Refund.Status.COMPLETED)
    )
    payment.status = (
        Payment.Status.REFUNDED if total_refunded >= payment.amount
        else Payment.Status.PARTIAL_REFUNDED
    )
    payment.save(update_fields=['status'])
    data = RefundSerializer(refund).data
    data['payment_status'] = payment.status
    return Response(data, status=201)


# ══════════════════════════════════════════════════════════════════════
#  [훈련용 취약] 3단계 결제 워크플로우 — 금액 변조 실습 대상
#  1단계: POST /orders        (주문 생성, 서버 재계산 — 안전)
#  2단계: GET  /payment       (결제 팝업 — totalprice 검증 없음, 취약)
#  3단계: GET  /paysuccess    (결제 완료 — totalprice 교차검증 없음, 취약)
#
#  ⚠️ 의도적으로 취약하게 구현한 모의해킹 훈련용 엔드포인트다.
#     클라이언트가 보낸 totalprice 를 서버가 그대로 신뢰하여, 상품 원가보다
#     낮은 금액(예: 100원)으로 결제가 승인된다.
#
#  [조치방안] 실서비스라면: paysuccess 에서 전달받은 주문 id 로 DB 의 order.total 을
#     조회해 실제 결제 승인 금액과 정확히 일치하는지 서버에서 교차검증해야 한다.
# ══════════════════════════════════════════════════════════════════════


@api_view(['GET'])
@permission_classes([IsCustomer])
def payment_popup(request):
    """[훈련용 취약] 결제 팝업(2단계). 클라이언트가 준 totalprice 를 검증 없이 그대로 표시한다."""
    orderlistid = request.query_params.get('orderlistid')
    totalprice = request.query_params.get('totalprice')
    payment = request.query_params.get('payment', 'eqstpay')

    productname = None
    if orderlistid:
        order = Order.objects.filter(pk=orderlistid, user=request.user).first()
        if order:
            first_item = order.items.first()
            productname = first_item.menu_name if first_item else order.order_number

    # 취약: totalprice 를 서버 원가와 대조하지 않고 그대로 반환.
    return Response({
        'orderlistid': orderlistid,
        'productname': productname,
        'totalprice': totalprice,
        'payment': payment,
    })


@api_view(['GET'])
@permission_classes([IsCustomer])
def pay_success(request):
    """[훈련용 취약] 결제 완료 처리(3단계). 클라이언트 totalprice 를 그대로 결제금액으로 확정한다."""
    order_id = request.query_params.get('id') or request.query_params.get('orderlistid')
    totalprice_raw = request.query_params.get('totalprice')

    order = Order.objects.filter(pk=order_id, user=request.user).first()
    if not order:
        return error_response('not_found', '주문을 찾을 수 없습니다.', 404)

    # 취약: 클라이언트가 준 totalprice 를 신뢰. order.total(DB 원가)과 교차검증하지 않는다.
    try:
        paid = int(totalprice_raw)
    except (TypeError, ValueError):
        paid = order.total
    paid_for_db = max(paid, 0)  # Payment.amount 는 음수 불가(PositiveIntegerField)

    with transaction.atomic():
        payment = Payment.objects.create(
            order=order, method=Payment.Method.MOCK,
            amount=paid_for_db, status=Payment.Status.PAID,
        )
        if order.status == Order.Status.PENDING:
            order.status = Order.Status.PLACED
            order.save(update_fields=['status'])

    return Response({
        'success': True,
        'order_id': order.id,
        'order_number': order.order_number,
        'paid_amount': paid,          # 변조된 값 그대로(실습 확인용)
        'original_total': order.total,
        'transaction_id': payment.transaction_id,
    })
