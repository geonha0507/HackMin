from pathlib import Path
from uuid import uuid4

from django.conf import settings
from django.db import models
from django.utils import timezone


def business_license_upload_to(instance, filename):
    extension = Path(filename).suffix.lower()
    file_id = uuid4().hex
    now = timezone.now()

    return f'licenses/{now:%Y/%m}/{file_id}{extension}'

class Restaurant(models.Model):
    owner = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE,
        related_name='restaurants', null=True, blank=True,
    )
    name = models.CharField(max_length=128)
    cuisine_type = models.CharField(max_length=255, blank=True)   # 종류(콤마구분, 복수가능): 한식,중식...
    description = models.TextField(blank=True)
    phone = models.CharField(max_length=32, blank=True)
    address = models.CharField(max_length=255, blank=True)
    latitude = models.FloatField(null=True, blank=True)
    longitude = models.FloatField(null=True, blank=True)
    min_order_amount = models.PositiveIntegerField(default=0)
    delivery_fee = models.PositiveIntegerField(default=0)
    rating = models.FloatField(default=0.0)
    is_open = models.BooleanField(default=True)
    business_license = models.FileField(
        upload_to=business_license_upload_to,
        null=True,
        blank=True,
    )
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return self.name


class RestaurantEditRequest(models.Model):
    """점주가 제출한 매장 정보 수정 요청. 관리자 승인 후에만 실제 반영된다."""

    class Status(models.TextChoices):
        PENDING = 'pending', 'Pending'
        APPROVED = 'approved', 'Approved'
        REJECTED = 'rejected', 'Rejected'

    restaurant = models.ForeignKey(Restaurant, on_delete=models.CASCADE, related_name='edit_requests')
    requested_by = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='+',
    )
    changes = models.JSONField()  # {field: new_value}
    status = models.CharField(max_length=16, choices=Status.choices, default=Status.PENDING)
    requested_at = models.DateTimeField(auto_now_add=True)
    processed_at = models.DateTimeField(null=True, blank=True)
    processed_by = models.ForeignKey(
        settings.AUTH_USER_MODEL, null=True, blank=True, on_delete=models.SET_NULL, related_name='+',
    )

    class Meta:
        ordering = ['-requested_at']

    def __str__(self):
        return f'{self.restaurant_id}:{self.status}'


class MenuCategory(models.Model):
    restaurant = models.ForeignKey(Restaurant, on_delete=models.CASCADE, related_name='categories')
    name = models.CharField(max_length=64)
    display_order = models.PositiveIntegerField(default=0)

    class Meta:
        ordering = ['display_order', 'id']

    def __str__(self):
        return self.name


class Menu(models.Model):
    class Status(models.TextChoices):
        ON_SALE = 'on_sale', 'On sale'
        SOLD_OUT = 'sold_out', 'Sold out'
        HIDDEN = 'hidden', 'Hidden'

    restaurant = models.ForeignKey(Restaurant, on_delete=models.CASCADE, related_name='menus')
    category = models.ForeignKey(
        MenuCategory, on_delete=models.SET_NULL, null=True, blank=True, related_name='menus',
    )
    name = models.CharField(max_length=128)
    description = models.TextField(blank=True)
    price = models.PositiveIntegerField(default=0)
    image = models.ImageField(upload_to='menus/', null=True, blank=True)
    status = models.CharField(max_length=16, choices=Status.choices, default=Status.ON_SALE)
    is_membership_only = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['category__display_order', 'id']

    def __str__(self):
        return self.name


class MenuOptionGroup(models.Model):
    menu = models.ForeignKey(Menu, on_delete=models.CASCADE, related_name='option_groups')
    name = models.CharField(max_length=64)                 # e.g. "맵기", "사이즈"
    is_required = models.BooleanField(default=False)
    max_select = models.PositiveIntegerField(default=1)

    def __str__(self):
        return self.name


class MenuOption(models.Model):
    group = models.ForeignKey(MenuOptionGroup, on_delete=models.CASCADE, related_name='options')
    name = models.CharField(max_length=64)
    extra_price = models.IntegerField(default=0)

    def __str__(self):
        return f'{self.name} (+{self.extra_price})'
