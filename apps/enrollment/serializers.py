"""입점 요청 Serializer."""

import os
from rest_framework import serializers
from .models import EnrollmentRequest


_ALLOWED_DOC_EXT = {'.pdf', '.jpg', '.jpeg', '.png'}
_MAX_DOC_BYTES = 10 * 1024 * 1024


class EnrollmentRequestSerializer(serializers.ModelSerializer):
    """입점 요청 제출 Serializer."""
    
    business_license = serializers.FileField(required=True)
    
    class Meta:
        model = EnrollmentRequest
        fields = ['id', 'username', 'password', 'phone', 'owner_name', 
                  'restaurant_name', 'business_license', 'created_at']
        extra_kwargs = {
            'password': {'write_only': True},
        }
    
    def validate_business_license(self, value):
        """사업자등록증 파일 검증."""
        ext = os.path.splitext(value.name)[1].lower()
        if ext not in _ALLOWED_DOC_EXT:
            raise serializers.ValidationError('허용되지 않는 파일 형식입니다. (pdf, jpg, jpeg, png만 가능)')
        if value.size > _MAX_DOC_BYTES:
            raise serializers.ValidationError('파일 크기는 10MB 이하만 허용됩니다.')
        return value


class EnrollmentRequestAdminSerializer(serializers.ModelSerializer):
    """Admin 목록 조회용 Serializer."""
    
    reviewed_by_username = serializers.CharField(source='reviewed_by.username', read_only=True, allow_null=True)
    business_license_url = serializers.SerializerMethodField()
    
    class Meta:
        model = EnrollmentRequest
        fields = ['id', 'username', 'phone', 'owner_name', 'restaurant_name',
                  'business_license_url', 'status', 'created_at', 'reviewed_at', 
                  'reviewed_by_username', 'rejection_reason']
        read_only_fields = ['id', 'username', 'phone', 'owner_name', 
                           'restaurant_name', 'business_license_url', 'created_at']
    
    def get_business_license_url(self, obj):
        if obj.business_license:
            return obj.business_license.url
        return None


class EnrollmentReviewSerializer(serializers.Serializer):
    """입점 요청 승인/거절 Serializer."""
    
    status = serializers.ChoiceField(choices=['approved', 'rejected'])
    rejection_reason = serializers.CharField(required=False, allow_blank=True)
