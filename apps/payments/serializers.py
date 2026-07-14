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
    # 🎯 Vulnerable 모드에서만 신뢰되는 클라이언트 제공 금액.
    amount = serializers.IntegerField(required=False)
