from django.contrib.auth import get_user_model
from rest_framework import serializers

from restaurants.models import Menu, MenuCategory, MenuOption, MenuOptionGroup, Restaurant

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


class ProductSerializer(serializers.ModelSerializer):
    class Meta:
        model = Menu
        fields = [
            'id', 'restaurant', 'category', 'name', 'description',
            'price', 'image', 'status', 'created_at',
        ]
        read_only_fields = ['id', 'image', 'created_at']


class CategorySerializer(serializers.ModelSerializer):
    class Meta:
        model = MenuCategory
        fields = ['id', 'restaurant', 'name', 'display_order']
        read_only_fields = ['id']


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
