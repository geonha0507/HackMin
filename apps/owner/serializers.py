from django.contrib.auth import get_user_model
from rest_framework import serializers

from restaurants.models import Menu, MenuCategory, MenuOption, MenuOptionGroup, Restaurant
from orders.serializers import OrderSerializer

User = get_user_model()


class OwnerSignupSerializer(serializers.Serializer):
    username = serializers.CharField()
    password = serializers.CharField(write_only=True, min_length=4)
    nickname = serializers.CharField(required=False, allow_blank=True, default='')
    restaurant_name = serializers.CharField(required=False, allow_blank=True, default='')

    def validate_username(self, value):
        if User.objects.filter(username=value).exists():
            raise serializers.ValidationError('이미 사용 중인 아이디입니다.')
        return value

    def create(self, validated_data):
        user = User(
            username=validated_data['username'],
            nickname=validated_data.get('nickname', ''),
            role=User.Role.OWNER,
        )
        user.set_password(validated_data['password'])
        user.save()
        name = validated_data.get('restaurant_name')
        if name:
            Restaurant.objects.create(owner=user, name=name)
        return user


class OwnerProfileSerializer(serializers.ModelSerializer):
    class Meta:
        model = User
        fields = ['id', 'username', 'nickname', 'email', 'phone', 'role']
        read_only_fields = ['id', 'username', 'role']


class OwnerPasswordChangeSerializer(serializers.Serializer):
    old_password = serializers.CharField(write_only=True)
    new_password = serializers.CharField(write_only=True, min_length=4)


# 비정상적으로 큰 가격 입력을 막는다. 예전에는 apps/web 화면에서만 검사해서
# API 를 직접 호출하면 우회가 가능했다. 규칙을 서버로 옮긴다.
MAX_MENU_PRICE = 10_000_000


class ProductSerializer(serializers.ModelSerializer):
    # 화면 표시용 파생 필드. 응답에 restaurant/category 가 id 뿐이라
    # 이름을 따로 내려주지 않으면 웹에서 표시할 방법이 없다.
    restaurant_name = serializers.CharField(source='restaurant.name', read_only=True, default='')
    category_name = serializers.CharField(source='category.name', read_only=True, default='')
    status_display = serializers.CharField(source='get_status_display', read_only=True)

    class Meta:
        model = Menu
        fields = [
            'id', 'restaurant', 'restaurant_name', 'category', 'category_name',
            'name', 'description', 'price', 'image', 'status', 'status_display',
            'created_at',
        ]
        read_only_fields = ['id', 'image', 'created_at']

    def validate_price(self, value):
        if value > MAX_MENU_PRICE:
            raise serializers.ValidationError(
                f'가격은 {MAX_MENU_PRICE:,}원 이하로 입력하세요.'
            )
        return value

    def validate_name(self, value):
        value = (value or '').strip()
        if not value:
            raise serializers.ValidationError('상품명을 입력하세요.')
        return value


class CategorySerializer(serializers.ModelSerializer):
    restaurant_name = serializers.CharField(source='restaurant.name', read_only=True, default='')

    class Meta:
        model = MenuCategory
        fields = ['id', 'restaurant', 'restaurant_name', 'name', 'display_order']
        read_only_fields = ['id']

    def validate_name(self, value):
        value = (value or '').strip()
        if not value:
            raise serializers.ValidationError('카테고리 이름을 입력하세요.')
        return value

    def validate(self, attrs):
        # 같은 매장 안에서 이름 중복을 막는다. 예전에는 apps/web 화면에서만
        # 검사했다.
        restaurant = attrs.get('restaurant') or getattr(self.instance, 'restaurant', None)
        name = attrs.get('name') or getattr(self.instance, 'name', None)
        if restaurant and name:
            dup = MenuCategory.objects.filter(restaurant=restaurant, name__iexact=name)
            if self.instance:
                dup = dup.exclude(pk=self.instance.pk)
            if dup.exists():
                raise serializers.ValidationError('이미 같은 이름의 카테고리가 있습니다.')
        return attrs


class OptionGroupSerializer(serializers.ModelSerializer):
    class Meta:
        model = MenuOptionGroup
        fields = ['id', 'menu', 'name', 'is_required', 'max_select']
        read_only_fields = ['id']


class OptionSerializer(serializers.ModelSerializer):
    class Meta:
        model = MenuOption
        fields = ['id', 'group', 'name', 'extra_price']
        read_only_fields = ['id']


class OwnerOrderSerializer(OrderSerializer):
    """점주 화면 전용 주문 표현.

    점주는 배달 처리를 위해 주문자 이름과 연락처가 필요하다. 이 정보는 고객용
    엔드포인트(/orders, /me/orders)로는 나가면 안 되므로 기본 OrderSerializer 를
    건드리지 않고 여기서만 확장한다.
    """

    customer_name = serializers.SerializerMethodField()
    customer_phone = serializers.CharField(source='user.phone', read_only=True, default='')

    class Meta(OrderSerializer.Meta):
        fields = OrderSerializer.Meta.fields + ['customer_name', 'customer_phone']
        read_only_fields = fields

    def get_customer_name(self, obj):
        if not obj.user:
            return ''
        return obj.user.nickname or obj.user.username


class OwnerRestaurantSerializer(serializers.ModelSerializer):
    """점주 본인 매장 표현. 공개 목록과 달리 주소와 승인 상태를 포함한다."""

    is_reviewable = serializers.SerializerMethodField()

    class Meta:
        model = Restaurant
        fields = [
            'id', 'name', 'cuisine_type', 'description', 'phone', 'address',
            'min_order_amount', 'delivery_fee', 'rating', 'image', 'is_open',
            'created_at', 'is_reviewable',
        ]
        read_only_fields = fields

    def get_is_reviewable(self, obj):
        from restaurants.selectors import is_reviewable_restaurant
        return is_reviewable_restaurant(obj)
