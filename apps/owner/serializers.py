import re

from django.contrib.auth import get_user_model
from rest_framework import serializers

from restaurants.models import Menu, MenuCategory, MenuOption, MenuOptionGroup, Restaurant
from orders.serializers import OrderSerializer
from reviews.serializers import ReviewSerializer

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


EMAIL_RE = re.compile(r'^[^@\s]+@[^@\s]+\.[^@\s]+$')


class OwnerProfileSerializer(serializers.ModelSerializer):
    # 마이페이지 화면이 쓰는 표시용 필드. 웹이 ORM 으로 user 객체를 직접 읽던
    # 시절에는 get_role_display() / date_joined 를 그냥 참조할 수 있었다.
    role_display = serializers.CharField(source='get_role_display', read_only=True)

    class Meta:
        model = User
        fields = ['id', 'username', 'nickname', 'email', 'phone', 'role',
                  'role_display', 'date_joined']
        read_only_fields = ['id', 'username', 'role', 'role_display', 'date_joined']

    def validate_email(self, value):
        # 형식·중복 검사는 apps/web 화면에만 있었다. API 로는 아무 문자열이나
        # 넣을 수 있었으므로 서버로 옮긴다.
        value = (value or '').strip()
        if not value:
            raise serializers.ValidationError('이메일을 입력해주세요.')
        if not EMAIL_RE.fullmatch(value):
            raise serializers.ValidationError('올바른 이메일 형식이 아닙니다.')
        dup = User.objects.filter(email__iexact=value)
        if self.instance:
            dup = dup.exclude(pk=self.instance.pk)
        if dup.exists():
            raise serializers.ValidationError('이미 사용 중인 이메일입니다.')
        return value


class OwnerPasswordChangeSerializer(serializers.Serializer):
    old_password = serializers.CharField(write_only=True)
    # apps/web 화면은 8자 이상을 요구했는데 API 는 4자였다. 강한 쪽으로 맞춘다.
    new_password = serializers.CharField(
        write_only=True, min_length=8,
        error_messages={'min_length': '새 비밀번호는 8자 이상이어야 합니다.'},
    )


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


class OwnerReviewSerializer(ReviewSerializer):
    """점주 리뷰 관리 화면용.

    기본 ReviewSerializer 에는 매장명과 소프트 삭제 정보가 없다. 고객용
    응답에 삭제 사유까지 실어 보낼 이유는 없으므로 여기서만 확장한다.
    """

    restaurant_name = serializers.CharField(source='restaurant.name', read_only=True, default='')
    author_name = serializers.SerializerMethodField()

    class Meta(ReviewSerializer.Meta):
        fields = ReviewSerializer.Meta.fields + [
            'restaurant_name', 'author_name',
            'is_deleted', 'delete_reason', 'deleted_at',
        ]
        read_only_fields = fields

    def get_author_name(self, obj):
        # ReviewSerializer.author 는 nickname 만 보는데 비어 있을 수 있다.
        if not obj.user:
            return '(탈퇴한 사용자)'
        return obj.user.nickname or obj.user.username


class WithdrawalRequestSerializer(serializers.ModelSerializer):
    status_display = serializers.CharField(source='get_status_display', read_only=True)

    class Meta:
        from accounts.models import WithdrawalRequest as _WR
        model = _WR
        fields = ['id', 'status', 'status_display', 'requested_at', 'processed_at']
        read_only_fields = fields
