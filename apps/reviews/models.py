from django.conf import settings
from django.db import models


def review_image_upload_to(instance, filename):
    if instance.review and instance.review.user:
        return f'reviews/{instance.review.user.user_id}_{filename}'
    return f'reviews/unknown_{filename}'


class Review(models.Model):
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='reviews',
    )
    restaurant = models.ForeignKey(
        'restaurants.Restaurant', on_delete=models.CASCADE, related_name='reviews',
    )
    order = models.ForeignKey(
        'orders.Order', on_delete=models.SET_NULL, null=True, blank=True, related_name='reviews',
    )
    rating = models.PositiveSmallIntegerField(default=5)   # 1..5
    content = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f'Review({self.user_id}->{self.restaurant_id}, {self.rating})'


class ReviewImage(models.Model):
    review = models.ForeignKey(Review, on_delete=models.CASCADE, related_name='images')
    image = models.ImageField(
        upload_to=review_image_upload_to  # ✅ user.user_id 사용
    )
    created_at = models.DateTimeField(auto_now_add=True)


class ReviewReply(models.Model):
    review = models.OneToOneField(Review, on_delete=models.CASCADE, related_name='reply')
    owner = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='review_replies',
    )
    content = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)
