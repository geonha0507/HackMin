from django.conf import settings
from django.core.files.storage import FileSystemStorage
from django.db import models


def review_image_upload_to(instance, filename):
    if instance.review and instance.review.user:
        return f'reviews/{instance.review.user.user_id}_{filename}'
    return f'reviews/unknown_{filename}'


def review_image_storage():
    """리뷰 이미지는 S3 가 아니라 도커 볼륨(/app/media)에 저장한다.

    USE_S3=1 이어도 DEFAULT_FILE_STORAGE(S3) 를 타지 않도록 필드에 로컬
    파일시스템 스토리지를 명시한다. 이렇게 하면 민감정보(사업자등록증 등)만
    S3 에 남고, 리뷰 이미지는 항상 볼륨에 저장돼 저장 공간이 분리된다.

    callable 로 넘겨서 마이그레이션에 경로가 하드코딩되지 않게 한다
    (경로/URL 은 REVIEW_IMAGE_ROOT / REVIEW_IMAGE_URL 환경변수로 조정).
    """
    return FileSystemStorage(
        location=str(settings.REVIEW_IMAGE_ROOT),
        base_url=settings.REVIEW_IMAGE_URL,
    )


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
    rating = models.FloatField(default=5.0)   # 1..5
    content = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    # 점주에 의한 소프트 삭제. 실제 행은 남기고 목록에서만 숨긴다.
    is_deleted = models.BooleanField(default=False)
    delete_reason = models.TextField(blank=True)
    deleted_at = models.DateTimeField(null=True, blank=True)
    deleted_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True, blank=True,
        related_name='deleted_reviews',
    )

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f'Review({self.user_id}->{self.restaurant_id}, {self.rating})'


class ReviewImage(models.Model):
    review = models.ForeignKey(Review, on_delete=models.CASCADE, related_name='images')
    image = models.ImageField(
        upload_to=review_image_upload_to,   # ✅ user.user_id 사용
        storage=review_image_storage,       # ✅ 항상 로컬 볼륨에 저장 (S3 제외)
    )
    created_at = models.DateTimeField(auto_now_add=True)


class ReviewReply(models.Model):
    review = models.OneToOneField(Review, on_delete=models.CASCADE, related_name='reply')
    owner = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='review_replies',
    )
    content = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)
