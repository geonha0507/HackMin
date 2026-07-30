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
    # 아래 두 필드는 web_bff(점주 웹 BFF) 화면용 추가분이다.
    # ORM 직결이던 시절에는 템플릿에서 order.restaurant.name /
    # order.get_status_display() 로 접근했지만, HTTP 응답의 restaurant 는
    # id(정수)뿐이라 화면에 표시할 수단이 없다. 기존 필드는 그대로 두고
    # 덧붙이기만 하므로 모바일 앱 등 기존 소비자에는 영향이 없다.
    restaurant_name = serializers.CharField(source='restaurant.name', read_only=True, default='')
    status_display = serializers.CharField(source='get_status_display', read_only=True)

    class Meta:
        model = Order
        fields = [
            'id', 'order_number', 'restaurant', 'restaurant_name',
            'status', 'status_display', 'subtotal',
            'delivery_fee', 'discount', 'total', 'address', 'address_detail',
            'request_note', 'items', 'created_at', 'updated_at',
        ]
        read_only_fields = fields


class OrderCreateSerializer(serializers.Serializer):
    address = serializers.CharField(required=False, allow_blank=True, default='')
    address_detail = serializers.CharField(required=False, allow_blank=True, default='')
    request_note = serializers.CharField(required=False, allow_blank=True, default='')
