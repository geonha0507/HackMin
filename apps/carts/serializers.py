from rest_framework import serializers

from restaurants.models import Menu
from .models import Cart, CartItem
from .services import compute_line_total, compute_unit_price


class CartItemSerializer(serializers.ModelSerializer):
    menu_name = serializers.CharField(source='menu.name', read_only=True)
    unit_price = serializers.SerializerMethodField()
    line_total = serializers.SerializerMethodField()

    class Meta:
        model = CartItem
        fields = ['id', 'menu', 'menu_name', 'quantity', 'options', 'unit_price', 'line_total']

    def get_unit_price(self, obj):
        return compute_unit_price(obj.menu, obj.options)

    def get_line_total(self, obj):
        return compute_line_total(obj.menu, obj.options, obj.quantity)


class CartItemCreateSerializer(serializers.Serializer):
    menu = serializers.PrimaryKeyRelatedField(queryset=Menu.objects.all())
    quantity = serializers.IntegerField(default=1)
    options = serializers.ListField(child=serializers.IntegerField(), required=False, default=list)


class CartSerializer(serializers.ModelSerializer):
    items = CartItemSerializer(many=True, read_only=True)

    class Meta:
        model = Cart
        fields = ['id', 'restaurant', 'coupon', 'items', 'updated_at']
