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
    answered_by_name = serializers.SerializerMethodField()

    class Meta:
        model = Inquiry
        fields = [
            'id', 'title', 'category', 'category_display', 'content',
            'author', 'images', 'created_at', 'updated_at',
            'answer', 'answered_at', 'answered_by_name', 'is_answered',
        ]
        read_only_fields = fields

    def get_answered_by_name(self, obj):
        return obj.answered_by.nickname if obj.answered_by else None


class InquiryCreateSerializer(serializers.Serializer):
    title = serializers.CharField(min_length=2, max_length=16)
    category = serializers.ChoiceField(choices=Inquiry.Category.choices)
    content = serializers.CharField(min_length=5, max_length=1000)


class InquiryAnswerSerializer(serializers.Serializer):
    """관리자 답변 입력용."""
    answer = serializers.CharField(min_length=1, max_length=2000)
