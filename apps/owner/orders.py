"""Owner order & payment management (/api/v1/owner/{orders,payments})."""

from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from common.exceptions import error_response
from common.mode import is_vulnerable
from common.permissions import IsOwner
from orders.models import Order
from orders.serializers import OrderSerializer
from payments.models import Payment, Refund
from payments.serializers import PaymentSerializer, RefundSerializer


def _order_scope(request):
    """🎯 Secure는 본인 매장 주문만, Vulnerable은 전체(BOLA)."""
    if is_vulnerable(request):
        return Order.objects.all()
    return Order.objects.filter(restaurant__owner=request.user)


@api_view(['GET'])
@permission_classes([IsOwner])
def owner_order_list(request):
    """🎯 신규·전체 주문 조회. ?status= 로 필터."""
    orders = _order_scope(request)
    status_filter = request.query_params.get('status')
    if status_filter:
        orders = orders.filter(status=status_filter)
    orders = orders.order_by('-created_at')
    return Response({'results': OrderSerializer(orders, many=True).data})


@api_view(['GET'])
@permission_classes([IsOwner])
def owner_order_detail(request, pk):
    order = _order_scope(request).filter(pk=pk).first()
    if not order:
        return error_response('not_found', '주문을 찾을 수 없습니다.', 404)
    return Response(OrderSerializer(order).data)


def _set_status(request, pk, new_status, allowed_from=None):
    order = _order_scope(request).filter(pk=pk).first()
    if not order:
        return None, error_response('not_found', '주문을 찾을 수 없습니다.', 404)
    if not is_vulnerable(request) and allowed_from is not None and order.status not in allowed_from:
        return None, error_response('invalid_transition', '허용되지 않는 상태 전이입니다.', 409)
    order.status = new_status
    order.save(update_fields=['status'])
    return order, None


@api_view(['POST'])
@permission_classes([IsOwner])
def accept_order(request, pk):
    order, err = _set_status(request, pk, Order.Status.ACCEPTED, {Order.Status.PLACED})
    return err or Response(OrderSerializer(order).data)


@api_view(['POST'])
@permission_classes([IsOwner])
def reject_order(request, pk):
    order, err = _set_status(request, pk, Order.Status.REJECTED, {Order.Status.PLACED})
    return err or Response(OrderSerializer(order).data)


@api_view(['PUT'])
@permission_classes([IsOwner])
def update_order_status(request, pk):
    """🎯 조리 시작/완료·배달 요청 등 상태 변경.

    Vulnerable 모드: 임의 주문에 임의 상태값 설정(BOLA + 상태 검증 없음).
    Secure 모드: 본인 매장 + 허용된 상태값만.
    """
    new_status = request.data.get('status')
    if not is_vulnerable(request) and new_status not in Order.Status.values:
        return error_response('bad_request', '유효하지 않은 상태입니다.', 400)
    order, err = _set_status(request, pk, new_status)
    return err or Response(OrderSerializer(order).data)


@api_view(['POST'])
@permission_classes([IsOwner])
def cancel_order(request, pk):
    order, err = _set_status(request, pk, Order.Status.CANCELLED)
    return err or Response(OrderSerializer(order).data)


# --- Payments --------------------------------------------------------------
def _payment_scope(request):
    if is_vulnerable(request):
        return Payment.objects.all()
    return Payment.objects.filter(order__restaurant__owner=request.user)


@api_view(['GET'])
@permission_classes([IsOwner])
def owner_payment_list(request):
    payments = _payment_scope(request).order_by('-created_at')
    return Response({'results': PaymentSerializer(payments, many=True).data})


@api_view(['GET'])
@permission_classes([IsOwner])
def owner_payment_detail(request, pk):
    payment = _payment_scope(request).filter(pk=pk).first()
    if not payment:
        return error_response('not_found', '결제 내역을 찾을 수 없습니다.', 404)
    return Response(PaymentSerializer(payment).data)


@api_view(['POST'])
@permission_classes([IsOwner])
def owner_payment_cancel(request, pk):
    payment = _payment_scope(request).filter(pk=pk).first()
    if not payment:
        return error_response('not_found', '결제 내역을 찾을 수 없습니다.', 404)
    payment.status = Payment.Status.CANCELLED
    payment.save(update_fields=['status'])
    return Response(PaymentSerializer(payment).data)


@api_view(['POST'])
@permission_classes([IsOwner])
def owner_payment_refund(request, pk):
    """🎯 전체/부분 환불.

    Vulnerable 모드: 소유권·금액 검증 없이 임의 금액 환불.
    Secure 모드: 본인 매장 결제 + 남은 환불 가능 금액 이내.
    """
    payment = _payment_scope(request).filter(pk=pk).first()
    if not payment:
        return error_response('not_found', '결제 내역을 찾을 수 없습니다.', 404)
    try:
        amount = int(request.data.get('amount', payment.amount))
    except (TypeError, ValueError):
        return error_response('bad_request', '유효한 금액이 아닙니다.', 400)

    if not is_vulnerable(request):
        already = sum(r.amount for r in payment.refunds.filter(status=Refund.Status.COMPLETED))
        remaining = payment.amount - already
        if amount <= 0 or amount > remaining:
            return error_response('invalid_amount', f'환불 가능 금액은 {remaining}원 입니다.', 400)

    refund = Refund.objects.create(
        payment=payment, amount=amount, reason=request.data.get('reason', ''),
        status=Refund.Status.COMPLETED,
    )
    total = sum(r.amount for r in payment.refunds.filter(status=Refund.Status.COMPLETED))
    payment.status = (
        Payment.Status.REFUNDED if total >= payment.amount else Payment.Status.PARTIAL_REFUNDED
    )
    payment.save(update_fields=['status'])
    return Response(RefundSerializer(refund).data, status=201)
