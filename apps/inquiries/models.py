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

    class Meta:
        ordering = ['created_at']

    def __str__(self):
        return f'Inquiry({self.user_id}, {self.title})'


class InquiryImage(models.Model):
    inquiry = models.ForeignKey(Inquiry, on_delete=models.CASCADE, related_name='images')
    image = models.ImageField(upload_to=inquiry_image_upload_to)
    created_at = models.DateTimeField(auto_now_add=True)
