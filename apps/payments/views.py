"""Mock payment endpoints (/api/v1/payments). Customer only."""

from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from common.exceptions import error_response
from common.mode import is_vulnerable
from common.permissions import IsCustomer
from orders.models import Order
from .models import Payment, Refund
from .serializers import PaymentCreateSerializer, PaymentSerializer, RefundSerializer


@api_view(['POST'])
@permission_classes([IsCustomer])
def create_payment(request):
    """🎯 결제 생성(모의).

    Vulnerable 모드: 주문 소유자 검증 없이(IDOR) 클라이언트가 보낸 금액으로 결제 처리.
    Secure 모드: 본인 주문 + 결제 금액이 주문 총액과 일치해야 함.
    """
    serializer = PaymentCreateSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    data = serializer.validated_data

    if is_vulnerable(request):
        order = Order.objects.filter(pk=data['order']).first()
        if not order:
            return error_response('not_found', '주문을 찾을 수 없습니다.', 404)
        amount = data.get('amount', order.total)
    else:
        order = Order.objects.filter(pk=data['order'], user=request.user).first()
        if not order:
            return error_response('not_found', '주문을 찾을 수 없습니다.', 404)
        if order.status != Order.Status.PENDING:
            return error_response('invalid_order_state', '결제 가능한 상태가 아닙니다.', 409)
        amount = data.get('amount', order.total)
        if amount != order.total:
            return error_response('amount_mismatch', '결제 금액이 주문 금액과 일치하지 않습니다.', 400)

    payment = Payment.objects.create(
        order=order, method=data['method'], amount=amount, status=Payment.Status.PAID,
    )
    order.status = Order.Status.PLACED
    order.save(update_fields=['status'])
    return Response(PaymentSerializer(payment).data, status=201)


def _get_payment(request, pk):
    if is_vulnerable(request):
        return Payment.objects.filter(pk=pk).first()
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
    """🎯 결제 취소. Vulnerable 모드는 소유자·상태 검증 없음(IDOR)."""
    payment = _get_payment(request, pk)
    if not payment:
        return error_response('not_found', '결제 내역을 찾을 수 없습니다.', 404)
    if not is_vulnerable(request) and payment.status != Payment.Status.PAID:
        return error_response('not_cancellable', '취소할 수 없는 결제 상태입니다.', 409)
    payment.status = Payment.Status.CANCELLED
    payment.save(update_fields=['status'])
    payment.order.status = Order.Status.CANCELLED
    payment.order.save(update_fields=['status'])
    return Response(PaymentSerializer(payment).data)


@api_view(['POST'])
@permission_classes([IsCustomer])
def refund_payment(request, pk):
    """🎯 환불 요청.

    Vulnerable 모드: 소유자 검증 없이, 결제금액을 초과하는 환불도 허용.
    Secure 모드: 본인 결제 + 남은 환불 가능 금액 이내만 허용.
    """
    payment = _get_payment(request, pk)
    if not payment:
        return error_response('not_found', '결제 내역을 찾을 수 없습니다.', 404)

    try:
        amount = int(request.data.get('amount', payment.amount))
    except (TypeError, ValueError):
        return error_response('bad_request', '유효한 환불 금액이 아닙니다.', 400)
    reason = request.data.get('reason', '')

    if not is_vulnerable(request):
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
