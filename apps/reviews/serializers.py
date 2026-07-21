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
    rating = serializers.FloatField(min_value=0.5, max_value=5)
    content = serializers.CharField(allow_blank=True, default='')

    def validate_rating(self, value):
        # 0.5 단위만 허용
        if (value * 2) % 1 != 0:
            raise serializers.ValidationError('별점은 0.5 단위로만 입력할 수 있습니다.')
        return value
