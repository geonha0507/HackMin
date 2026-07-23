from rest_framework import serializers

from .models import Inquiry, InquiryImage


class InquiryImageSerializer(serializers.ModelSerializer):
    class Meta:
        model = InquiryImage
        fields = ['id', 'image', 'created_at']


class InquirySerializer(serializers.ModelSerializer):
    author = serializers.CharField(source='user.nickname', read_only=True)
    category_display = serializers.CharField(source='get_category_display', read_only=True)
    images = InquiryImageSerializer(many=True, read_only=True)

    class Meta:
        model = Inquiry
        fields = [
            'id', 'title', 'category', 'category_display', 'content',
            'author', 'images', 'created_at', 'updated_at',
        ]
        read_only_fields = fields


class InquiryCreateSerializer(serializers.Serializer):
    title = serializers.CharField(min_length=2, max_length=16)
    category = serializers.ChoiceField(choices=Inquiry.Category.choices)
    content = serializers.CharField(min_length=5, max_length=1000)
