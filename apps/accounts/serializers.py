from django.contrib.auth import get_user_model
from rest_framework import serializers

from .models import Address
from .crypto_utils import encrypt_aes128

User = get_user_model()


class UserSerializer(serializers.ModelSerializer):
    """사용자 정보 조회용"""
    class Meta:
        model = User
        fields = ['id', 'username', 'email', 'phone', 'nickname', 'name', 'role', 'status', 
                  'bank_name', 'account_number', 'card_number', 'card_cvc', 'card_password_2digit', 'date_joined']
        read_only_fields = ['id', 'role', 'status', 'date_joined']


class SignupSerializer(serializers.ModelSerializer):
    """회원가입용 - 점주는 계좌번호 필수, 사용자는 카드정보 내정보에서 등록"""
    password = serializers.CharField(write_only=True, min_length=4)
    bank_name = serializers.CharField(write_only=True, required=False, help_text="은행명 (점주웹)")
    account_number = serializers.CharField(write_only=True, required=False, help_text="계좌번호 (점주웹)")
    role = serializers.CharField(write_only=True, required=False, help_text="role (owner 또는 customer)")

    class Meta:
        model = User
        fields = ['username', 'password', 'email', 'phone', 'nickname', 'name', 'role',
                  'bank_name', 'account_number']

    def validate_username(self, value):
        if User.objects.filter(username=value).exists():
            raise serializers.ValidationError('이미 사용 중인 아이디입니다.')
        return value

    def validate(self, attrs):
        """역할별 필수 필드 검증 - 점주는 계좌정보 필수"""
        role = attrs.get('role', User.Role.CUSTOMER)
        
        if role == User.Role.OWNER:
            bank_name = attrs.get('bank_name')
            account_number = attrs.get('account_number')
            
            if not bank_name or not account_number:
                raise serializers.ValidationError('점주웹의 경우 은행명과 계좌번호는 필수입니다.')
        
        return attrs

    def create(self, validated_data):
        password = validated_data.pop('password')
        role = validated_data.pop('role', User.Role.CUSTOMER)
        
        bank_name = validated_data.pop('bank_name', None)
        account_number = validated_data.pop('account_number', None)
        
        # 점주: 계좌번호 암호화
        account_number_encrypted = None
        if account_number:
            try:
                account_number_encrypted = encrypt_aes128(account_number)
            except Exception as e:
                raise serializers.ValidationError(f'계좌번호 암호화 중 오류가 발생했습니다: {str(e)}')
        
        user = User(
            role=role,
            bank_name=bank_name,
            account_number=account_number_encrypted,
            **validated_data
        )
        user.set_password(password)
        user.save()
        return user


class UserPaymentSerializer(serializers.ModelSerializer):
    """사용자 내정보에서 카드정보 등록/수정용"""
    card_number = serializers.CharField(write_only=True, required=True, help_text="카드번호")
    card_cvc = serializers.CharField(write_only=True, required=True, help_text="카드 CVC")
    card_password_2digit = serializers.CharField(write_only=True, required=True, help_text="카드 비밀번호 2자리")

    class Meta:
        model = User
        fields = ['card_number', 'card_cvc', 'card_password_2digit']

    def validate_card_number(self, value):
        if not value or len(value.replace('-', '')) < 13:
            raise serializers.ValidationError('올바른 카드번호를 입력해주세요.')
        return value

    def validate_card_cvc(self, value):
        if not value or len(value) < 3:
            raise serializers.ValidationError('올바른 CVC를 입력해주세요.')
        return value

    def validate_card_password_2digit(self, value):
        if not value or len(value) != 2 or not value.isdigit():
            raise serializers.ValidationError('카드 비밀번호는 2자리 숫자여야 합니다.')
        return value

    def update(self, instance, validated_data):
        card_number = validated_data.get('card_number')
        card_cvc = validated_data.get('card_cvc')
        card_password_2digit = validated_data.get('card_password_2digit')
        
        # 카드정보 암호화
        try:
            if card_number:
                instance.card_number = encrypt_aes128(card_number)
            if card_cvc:
                instance.card_cvc = encrypt_aes128(card_cvc)
            if card_password_2digit:
                instance.card_password_2digit = encrypt_aes128(card_password_2digit)
        except Exception as e:
            raise serializers.ValidationError(f'카드정보 암호화 중 오류가 발생했습니다: {str(e)}')
        
        instance.save()
        return instance


class UserProfileUpdateSerializer(serializers.ModelSerializer):
    """사용자 프로필 수정용 (기본 정보)"""
    class Meta:
        model = User
        fields = ['email', 'phone', 'nickname', 'name']


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
