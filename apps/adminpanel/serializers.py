import os

from rest_framework import serializers

from .models import Notice

_ALLOWED_IMAGE_EXT = {'.jpg', '.jpeg', '.png', '.gif', '.webp'}
_MAX_IMAGE_BYTES = 5 * 1024 * 1024


class NoticeSerializer(serializers.ModelSerializer):
    class Meta:
        model = Notice
        fields = ['id', 'title', 'content', 'image', 'is_pinned', 'created_at', 'updated_at']
        read_only_fields = ['id', 'created_at', 'updated_at']

    def validate_title(self, value):
        value = (value or '').strip()
        if not value:
            raise serializers.ValidationError('제목을 입력하세요.')
        return value

    def validate_content(self, value):
        value = (value or '').strip()
        if not value:
            raise serializers.ValidationError('내용을 입력하세요.')
        return value

    def validate_image(self, value):
        # 확장자·크기 검사는 apps/admin_web 화면에만 있었다. API 로는 무엇이든
        # 올릴 수 있었으므로 서버로 옮긴다.
        if not value:
            return value
        ext = os.path.splitext(value.name)[1].lower()
        if ext not in _ALLOWED_IMAGE_EXT:
            raise serializers.ValidationError('이미지는 jpg/png/gif/webp 만 가능합니다.')
        if value.size > _MAX_IMAGE_BYTES:
            raise serializers.ValidationError('이미지는 5MB 이하만 가능합니다.')
        return value
