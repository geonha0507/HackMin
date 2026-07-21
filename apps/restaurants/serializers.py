from rest_framework import serializers

from .models import Menu, MenuCategory, MenuOption, MenuOptionGroup, Restaurant


class RestaurantListSerializer(serializers.ModelSerializer):
    class Meta:
        model = Restaurant
        fields = [
            'id', 'name', 'cuisine_type', 'rating', 'image', 'min_order_amount',
            'delivery_fee', 'is_open', 'latitude', 'longitude',
        ]


class RestaurantDetailSerializer(serializers.ModelSerializer):
    class Meta:
        model = Restaurant
        fields = [
            'id', 'name', 'cuisine_type', 'description', 'phone', 'address',
            'latitude', 'longitude', 'min_order_amount', 'delivery_fee',
            'rating', 'image', 'is_open', 'created_at',
        ]


class MenuOptionSerializer(serializers.ModelSerializer):
    class Meta:
        model = MenuOption
        fields = ['id', 'name', 'extra_price']


class MenuOptionGroupSerializer(serializers.ModelSerializer):
    options = MenuOptionSerializer(many=True, read_only=True)

    class Meta:
        model = MenuOptionGroup
        fields = ['id', 'name', 'is_required', 'max_select', 'options']


class MenuListSerializer(serializers.ModelSerializer):
    class Meta:
        model = Menu
        fields = ['id', 'name', 'description', 'price', 'image', 'status', 'category', 'is_membership_only']


class MenuDetailSerializer(serializers.ModelSerializer):
    option_groups = MenuOptionGroupSerializer(many=True, read_only=True)

    class Meta:
        model = Menu
        fields = [
            'id', 'restaurant', 'category', 'name', 'description', 'price',
            'image', 'status', 'is_membership_only', 'option_groups',
        ]


class MenuCategorySerializer(serializers.ModelSerializer):
    class Meta:
        model = MenuCategory
        fields = ['id', 'name', 'display_order']


class RestaurantReviewSerializer(serializers.Serializer):
    """Read-only view of a restaurant's reviews (write CRUD lives in reviews app)."""

    id = serializers.IntegerField()
    user = serializers.CharField(source='user.nickname')
    rating = serializers.FloatField()
    content = serializers.CharField()
    image_urls = serializers.SerializerMethodField()
    owner_reply = serializers.SerializerMethodField()
    created_at = serializers.DateTimeField()

    def get_image_urls(self, obj):
        return [img.image.url for img in obj.images.all() if img.image]

    def get_owner_reply(self, obj):
        try:
            return obj.reply.content
        except Exception:
            return None
