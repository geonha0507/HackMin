from rest_framework import serializers

from .models import Payment, Refund


class RefundSerializer(serializers.ModelSerializer):
    class Meta:
        model = Refund
        fields = ['id', 'payment', 'amount', 'reason', 'status', 'created_at']
        read_only_fields = ['id', 'status', 'created_at']


class PaymentSerializer(serializers.ModelSerializer):
    refunds = RefundSerializer(many=True, read_only=True)
    # 화면 표시용 파생 필드. 응답의 order 는 id 뿐이고 status/method 는 코드값이라
    # ORM 직결이던 시절 템플릿이 쓰던 order.order_number / get_*_display 를 대신한다.
    order_number = serializers.CharField(source='order.order_number', read_only=True, default='')
    restaurant_name = serializers.CharField(
        source='order.restaurant.name', read_only=True, default='')
    status_display = serializers.CharField(source='get_status_display', read_only=True)
    method_display = serializers.CharField(source='get_method_display', read_only=True)

    class Meta:
        model = Payment
        fields = [
            'id', 'order', 'order_number', 'restaurant_name',
            'method', 'method_display', 'amount', 'status', 'status_display',
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
