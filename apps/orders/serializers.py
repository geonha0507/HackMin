from rest_framework import serializers

from .models import Order, OrderItem


class OrderItemSerializer(serializers.ModelSerializer):
    menu_image = serializers.SerializerMethodField()

    class Meta:
        model = OrderItem
        fields = ['id', 'menu', 'menu_name', 'menu_image', 'unit_price', 'quantity', 'options', 'line_total']

    def get_menu_image(self, obj):
        # 주문 항목은 메뉴명만 스냅샷하므로, 이미지는 현재 메뉴에서 가져온다.
        # 메뉴가 삭제(SET_NULL)됐거나 이미지가 없으면 None.
        if obj.menu and obj.menu.image:
            return obj.menu.image.url
        return None


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
