from rest_framework import serializers

from .models import Notice


class NoticeSerializer(serializers.ModelSerializer):
    class Meta:
        model = Notice
        fields = ['id', 'title', 'content', 'image', 'is_pinned', 'created_at', 'updated_at']
        read_only_fields = ['id', 'created_at', 'updated_at']
