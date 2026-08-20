from rest_framework import serializers

from restaurants.models import Menu
from .models import Delivery, RiderLocation


def _restaurant_image_url(delivery):
    """배달 주문의 음식점 대표 이미지 URL(없으면 None). 상대경로(/media/...)로 내려가면
    앱의 ImageLoader가 서버 오리진을 붙여 절대 URL로 만든다(고객 앱과 동일 처리)."""
    restaurant = getattr(delivery.order, 'restaurant', None)
    if restaurant and restaurant.image:
        return restaurant.image.url
    return None


class DeliveryListSerializer(serializers.ModelSerializer):
    order_number = serializers.CharField(source='order.order_number', read_only=True)
    restaurant = serializers.CharField(source='order.restaurant.name', read_only=True, default='')
    restaurant_image = serializers.SerializerMethodField()
    total = serializers.IntegerField(source='order.total', read_only=True)

    class Meta:
        model = Delivery
        fields = ['id', 'order', 'order_number', 'restaurant', 'restaurant_image',
                  'total', 'status', 'assigned_at']

    def get_restaurant_image(self, obj):
        return _restaurant_image_url(obj)


class DeliveryDetailSerializer(serializers.ModelSerializer):
    """배달 상세: 배송지·연락처 등 개인정보 포함."""
    order_number = serializers.CharField(source='order.order_number', read_only=True)
    restaurant = serializers.CharField(source='order.restaurant.name', read_only=True, default='')
    restaurant_image = serializers.SerializerMethodField()
    customer = serializers.CharField(source='order.user.nickname', read_only=True)
    phone = serializers.CharField(source='order.user.phone', read_only=True)
    address = serializers.CharField(source='order.address', read_only=True)
    address_detail = serializers.CharField(source='order.address_detail', read_only=True)
    request_note = serializers.CharField(source='order.request_note', read_only=True)

    class Meta:
        model = Delivery
        fields = [
            'id', 'order', 'order_number', 'status', 'restaurant', 'restaurant_image',
            'customer', 'phone', 'address', 'address_detail', 'request_note',
            'assigned_at', 'completed_at',
        ]

    def get_restaurant_image(self, obj):
        return _restaurant_image_url(obj)


class RiderMenuSerializer(serializers.ModelSerializer):
    """홈에 노출할 메뉴(음식). 해킹의 민족 전체 메뉴를 매장명·사진과 함께 내려준다."""
    restaurant = serializers.CharField(source='restaurant.name', read_only=True, default='')
    image = serializers.SerializerMethodField()

    class Meta:
        model = Menu
        fields = ['id', 'name', 'restaurant', 'price', 'image']

    def get_image(self, obj):
        # 상대경로(/media/...)로 내려가면 앱 ImageLoader가 절대 URL로 만든다(고객 앱과 동일).
        return obj.image.url if obj.image else None


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
