from django.conf import settings
from django.db import models


def inquiry_image_upload_to(instance, filename):
    if instance.inquiry and instance.inquiry.user:
        return f'inquiries/{instance.inquiry.user.user_id}_{filename}'
    return f'inquiries/unknown_{filename}'


class Inquiry(models.Model):
    class Category(models.TextChoices):
        ORDER = 'order', '주문/결제'
        DELIVERY = 'delivery', '배달'
        REFUND = 'refund', '환불/교환'
        COUPON = 'coupon', '쿠폰/프로모션'
        ACCOUNT = 'account', '계정'
        ETC = 'etc', '기타'

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='inquiries',
    )
    title = models.CharField(max_length=16)
    category = models.CharField(max_length=20, choices=Category.choices)
    content = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    # 관리자 답변 (1문의 : 1답변). 미답변이면 answer 는 빈 문자열로 둔다.
    answer = models.TextField(blank=True, default='')
    answered_at = models.DateTimeField(null=True, blank=True)
    answered_by = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.SET_NULL,
        null=True, blank=True, related_name='answered_inquiries',
    )

    class Meta:
        ordering = ['created_at']

    def __str__(self):
        return f'Inquiry({self.user_id}, {self.title})'

    @property
    def is_answered(self):
        return bool(self.answer)


class InquiryImage(models.Model):
    inquiry = models.ForeignKey(Inquiry, on_delete=models.CASCADE, related_name='images')
    image = models.ImageField(upload_to=inquiry_image_upload_to)
    created_at = models.DateTimeField(auto_now_add=True)
