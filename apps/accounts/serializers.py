from django.contrib.auth import get_user_model
from rest_framework import serializers

from .models import Address
from .crypto_utils import encrypt_aes128

User = get_user_model()


class UserSerializer(serializers.ModelSerializer):
    class Meta:
        model = User
        fields = ['id', 'username', 'email', 'phone', 'nickname', 'role', 'status', 'date_joined']
        read_only_fields = ['id', 'role', 'status', 'date_joined']


class SignupSerializer(serializers.ModelSerializer):
    password = serializers.CharField(write_only=True, min_length=4)
    rrn = serializers.CharField(write_only=True, required=True, help_text="주민등록번호 (예: 990101-1234567)")

    class Meta:
        model = User
        fields = ['username', 'password', 'email', 'phone', 'nickname', 'name', 'rrn']

    def validate_username(self, value):
        if User.objects.filter(username=value).exists():
            raise serializers.ValidationError('이미 사용 중인 아이디입니다.')
        return value

    def validate_rrn(self, value):
        """주민등록번호 형식 검증 (XXXXXX-XXXXXXX)."""
        if not value:
            raise serializers.ValidationError('주민등록번호는 필수입니다.')
        
        # 기본 형식 검증: 숫자와 하이픈만 허용
        value_clean = value.replace('-', '')
        if not value_clean.isdigit() or len(value_clean) != 13:
            raise serializers.ValidationError('주민등록번호 형식이 올바르지 않습니다. (XXXXXX-XXXXXXX)')
        
        return value

    def create(self, validated_data):
        password = validated_data.pop('password')
        rrn = validated_data.pop('rrn')
        
        # 주민등록번호 암호화
        try:
            rrn_encrypted = encrypt_aes128(rrn)
        except Exception as e:
            raise serializers.ValidationError(f'주민등록번호 암호화 중 오류가 발생했습니다: {str(e)}')
        
        user = User(
            role=User.Role.CUSTOMER,
            rrn_encrypted=rrn_encrypted,
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
