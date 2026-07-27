from pathlib import Path
from uuid import uuid4

from django.conf import settings
from django.db import models
from django.utils import timezone


def notice_image_upload_to(instance, filename):
    extension = Path(filename).suffix.lower()
    file_id = uuid4().hex
    now = timezone.now()
    return f'notices/{now:%Y/%m}/{file_id}{extension}'


class Notice(models.Model):
    """앱 공지사항. 관리자가 작성하고 전체 사용자에게 노출된다."""

    title = models.CharField(max_length=200)
    content = models.TextField()
    image = models.ImageField(upload_to=notice_image_upload_to, null=True, blank=True)
    is_pinned = models.BooleanField(default=False)
    created_by = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True, related_name='+',
    )
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-is_pinned', '-created_at']

    def __str__(self):
        return self.title


class MetricSnapshot(models.Model):
    """관리자 대시보드 지표의 '증감 비교 기준' 스냅샷.

    관리자가 대시보드에서 '기준 갱신' 버튼을 누른 시점의 수치를 저장한다.
    이후 대시보드는 현재 수치와 가장 최근 스냅샷의 차이를 ▲/▼로 표시한다.
    (별도 스케줄러 없이 버튼을 누를 때만 기준이 갱신된다.)
    """

    user_count = models.IntegerField(default=0)
    owner_count = models.IntegerField(default=0)
    restaurant_count = models.IntegerField(default=0)
    order_count = models.IntegerField(default=0)
    suspended_count = models.IntegerField(default=0)
    total_sales = models.DecimalField(max_digits=14, decimal_places=2, default=0)
    created_by = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True, related_name='+',
    )
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f'MetricSnapshot({self.created_at:%Y-%m-%d %H:%M})'
