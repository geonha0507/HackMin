from rest_framework import serializers

from .models import Review, ReviewImage, ReviewReply


class ReviewImageSerializer(serializers.ModelSerializer):
    class Meta:
        model = ReviewImage
        fields = ['id', 'image', 'created_at']


class ReviewReplySerializer(serializers.ModelSerializer):
    class Meta:
        model = ReviewReply
        fields = ['id', 'content', 'created_at']


class ReviewSerializer(serializers.ModelSerializer):
    images = ReviewImageSerializer(many=True, read_only=True)
    reply = ReviewReplySerializer(read_only=True)
    author = serializers.CharField(source='user.nickname', read_only=True)

    class Meta:
        model = Review
        fields = [
            'id', 'restaurant', 'order', 'author', 'rating', 'content',
            'images', 'reply', 'created_at', 'updated_at',
        ]
        read_only_fields = ['id', 'author', 'images', 'reply', 'created_at', 'updated_at']


class ReviewCreateSerializer(serializers.Serializer):
    restaurant = serializers.IntegerField()
    order = serializers.IntegerField(required=False, allow_null=True)
    rating = serializers.IntegerField(min_value=1, max_value=5)
    content = serializers.CharField(allow_blank=True, default='')
