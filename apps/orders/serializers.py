from rest_framework import serializers

from .models import Order, OrderItem


class OrderItemSerializer(serializers.ModelSerializer):
    class Meta:
        model = OrderItem
        fields = ['id', 'menu', 'menu_name', 'unit_price', 'quantity', 'options', 'line_total']


class OrderSerializer(serializers.ModelSerializer):
    items = OrderItemSerializer(many=True, read_only=True)

    class Meta:
        model = Order
        fields = [
            'id', 'order_number', 'restaurant', 'status', 'subtotal',
            'delivery_fee', 'discount', 'total', 'address', 'address_detail',
            'request_note', 'items', 'created_at', 'updated_at',
        ]
        read_only_fields = fields


class OrderCreateSerializer(serializers.Serializer):
    address = serializers.CharField(required=False, allow_blank=True, default='')
    address_detail = serializers.CharField(required=False, allow_blank=True, default='')
    request_note = serializers.CharField(required=False, allow_blank=True, default='')
    # 🎯 Vulnerable 모드에서만 신뢰되는 클라이언트 제공 금액(가격 조작 실습용).
    total = serializers.IntegerField(required=False)
    discount = serializers.IntegerField(required=False)
