from django.contrib.auth import get_user_model
from rest_framework import serializers

from .models import Address
from .crypto_utils import encrypt_aes128

User = get_user_model()


class UserSerializer(serializers.ModelSerializer):
    class Meta:
        model = User
        fields = ['id', 'username', 'email', 'phone', 'nickname', 'name', 'role', 'status', 'date_joined']
        read_only_fields = ['id', 'role', 'status', 'date_joined']


class SignupSerializer(serializers.ModelSerializer):
    password = serializers.CharField(write_only=True, min_length=4)
    account_number = serializers.CharField(write_only=True, required=True, help_text="계좌번호 (숫자, 하이픈 허용)")

    class Meta:
        model = User
        fields = ['username', 'password', 'email', 'phone', 'nickname', 'name', 'account_number']

    def validate_username(self, value):
        if User.objects.filter(username=value).exists():
            raise serializers.ValidationError('이미 사용 중인 아이디입니다.')
        return value

    def validate_account_number(self, value):
        """계좌번호 형식 검증 (하이픈 제외 8~20자리 숫자)."""
        if not value:
            raise serializers.ValidationError('계좌번호는 필수입니다.')

        digits = value.replace('-', '')
        if not digits.isdigit() or not (8 <= len(digits) <= 20):
            raise serializers.ValidationError('계좌번호 형식이 올바르지 않습니다.')

        return value

    def create(self, validated_data):
        password = validated_data.pop('password')
        account_number = validated_data.pop('account_number')

        # 계좌번호 암호화 (기존 AES-128 방식 재사용)
        try:
            account_number_encrypted = encrypt_aes128(account_number)
        except Exception as e:
            raise serializers.ValidationError(f'계좌번호 암호화 중 오류가 발생했습니다: {str(e)}')

        user = User(
            role=User.Role.CUSTOMER,
            account_number_encrypted=account_number_encrypted,
            **validated_data
        )
        user.set_password(password)
        user.save()
        return user


class LoginSerializer(serializers.Serializer):
    username = serializers.CharField()
    password = serializers.CharField(write_only=True)


class PasswordChangeSerializer(serializers.Serializer):
    old_password = serializers.CharField(write_only=True)
    new_password = serializers.CharField(write_only=True, min_length=4)


class AddressSerializer(serializers.ModelSerializer):
    class Meta:
        model = Address
        fields = [
            'id', 'label', 'address', 'detail', 'postal_code',
            'latitude', 'longitude', 'is_default', 'created_at',
        ]
        read_only_fields = ['id', 'created_at']
