from rest_framework import serializers

from .models import Delivery, RiderLocation


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


class RiderLocationSerializer(serializers.ModelSerializer):
    """라이더 위치 조회/저장. 위도·경도는 필수, 정확도(오차 m)는 선택."""

    class Meta:
        model = RiderLocation
        fields = ['latitude', 'longitude', 'accuracy', 'updated_at']
        read_only_fields = ['updated_at']

    def validate_latitude(self, value):
        if not -90 <= value <= 90:
            raise serializers.ValidationError('위도는 -90~90 범위여야 합니다.')
        return value

    def validate_longitude(self, value):
        if not -180 <= value <= 180:
            raise serializers.ValidationError('경도는 -180~180 범위여야 합니다.')
        return value
