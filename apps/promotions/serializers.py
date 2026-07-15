from rest_framework import serializers

from restaurants.models import Restaurant
from .models import Coupon, Favorite, Membership, MembershipPayment, UserCoupon


class CouponPublicSerializer(serializers.ModelSerializer):
    """다운로드 가능 목록용(코드 비노출)."""
    class Meta:
        model = Coupon
        fields = ['id', 'name', 'discount_type', 'discount_value', 'min_order_amount', 'valid_until']


class CouponFullSerializer(serializers.ModelSerializer):
    """본인이 보유한 쿠폰 조회용(코드 포함). /me/coupons 에서만 사용한다."""
    class Meta:
        model = Coupon
        fields = [
            'id', 'code', 'name', 'discount_type', 'discount_value',
            'min_order_amount', 'valid_until', 'is_active',
        ]


class UserCouponSerializer(serializers.ModelSerializer):
    coupon = CouponFullSerializer(read_only=True)

    class Meta:
        model = UserCoupon
        fields = ['id', 'coupon', 'is_used', 'downloaded_at', 'used_at']


class FavoriteSerializer(serializers.ModelSerializer):
    restaurant_name = serializers.CharField(source='restaurant.name', read_only=True)

    class Meta:
        model = Favorite
        fields = ['id', 'restaurant', 'restaurant_name', 'created_at']
        read_only_fields = ['id', 'created_at']

    def validate_restaurant(self, value):
        if not Restaurant.objects.filter(pk=value.pk).exists():
            raise serializers.ValidationError('음식점을 찾을 수 없습니다.')
        return value


class MembershipSerializer(serializers.ModelSerializer):
    class Meta:
        model = Membership
        fields = ['id', 'plan', 'status', 'started_at', 'cancelled_at']


class MembershipPaymentSerializer(serializers.ModelSerializer):
    class Meta:
        model = MembershipPayment
        fields = ['id', 'amount', 'paid_at']
