from rest_framework import serializers

from .models import Delivery


class DeliveryListSerializer(serializers.ModelSerializer):
    order_number = serializers.CharField(source='order.order_number', read_only=True)
    restaurant = serializers.CharField(source='order.restaurant.name', read_only=True, default='')
    total = serializers.IntegerField(source='order.total', read_only=True)

    class Meta:
        model = Delivery
        fields = ['id', 'order', 'order_number', 'restaurant', 'total', 'status', 'assigned_at']


class DeliveryDetailSerializer(serializers.ModelSerializer):
    """배달 상세: 배송지·연락처 등 개인정보 포함."""
    order_number = serializers.CharField(source='order.order_number', read_only=True)
    customer = serializers.CharField(source='order.user.nickname', read_only=True)
    phone = serializers.CharField(source='order.user.phone', read_only=True)
    address = serializers.CharField(source='order.address', read_only=True)
    address_detail = serializers.CharField(source='order.address_detail', read_only=True)
    request_note = serializers.CharField(source='order.request_note', read_only=True)

    class Meta:
        model = Delivery
        fields = [
            'id', 'order', 'order_number', 'status', 'customer', 'phone',
            'address', 'address_detail', 'request_note', 'assigned_at', 'completed_at',
        ]
