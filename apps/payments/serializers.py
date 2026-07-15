from rest_framework import serializers

from .models import Payment, Refund


class RefundSerializer(serializers.ModelSerializer):
    class Meta:
        model = Refund
        fields = ['id', 'payment', 'amount', 'reason', 'status', 'created_at']
        read_only_fields = ['id', 'status', 'created_at']


class PaymentSerializer(serializers.ModelSerializer):
    refunds = RefundSerializer(many=True, read_only=True)

    class Meta:
        model = Payment
        fields = [
            'id', 'order', 'method', 'amount', 'status',
            'transaction_id', 'refunds', 'created_at',
        ]
        read_only_fields = ['id', 'status', 'transaction_id', 'created_at']


class PaymentCreateSerializer(serializers.Serializer):
    order = serializers.IntegerField()
    method = serializers.ChoiceField(
        choices=Payment.Method.choices, default=Payment.Method.MOCK,
    )
    # 생략 시 주문 총액을 사용하며, 값을 보내면 주문 총액과 정확히 일치해야 한다(views.create_payment).
    amount = serializers.IntegerField(required=False)
