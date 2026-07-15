from django.conf import settings
from django.db import models


class Coupon(models.Model):
    class DiscountType(models.TextChoices):
        FIXED = 'fixed', 'Fixed amount'
        PERCENT = 'percent', 'Percentage'

    code = models.CharField(max_length=32, unique=True)
    name = models.CharField(max_length=128)
    discount_type = models.CharField(max_length=16, choices=DiscountType.choices, default=DiscountType.FIXED)
    discount_value = models.PositiveIntegerField(default=0)
    min_order_amount = models.PositiveIntegerField(default=0)
    valid_until = models.DateTimeField(null=True, blank=True)
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f'{self.code} ({self.name})'

    def compute_discount(self, amount):
        if amount < self.min_order_amount:
            return 0
        if self.discount_type == self.DiscountType.PERCENT:
            return amount * self.discount_value // 100
        return min(self.discount_value, amount)


class UserCoupon(models.Model):
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='user_coupons',
    )
    coupon = models.ForeignKey(Coupon, on_delete=models.CASCADE, related_name='user_coupons')
    is_used = models.BooleanField(default=False)
    downloaded_at = models.DateTimeField(auto_now_add=True)
    used_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        unique_together = [('user', 'coupon')]


class Favorite(models.Model):
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='favorites',
    )
    restaurant = models.ForeignKey(
        'restaurants.Restaurant', on_delete=models.CASCADE, related_name='favorited_by',
    )
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = [('user', 'restaurant')]


class Membership(models.Model):
    class Plan(models.TextChoices):
        BASIC = 'basic', 'Basic'
        PREMIUM = 'premium', 'Premium'

    class Status(models.TextChoices):
        ACTIVE = 'active', 'Active'
        CANCELLED = 'cancelled', 'Cancelled'

    user = models.OneToOneField(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='membership',
    )
    plan = models.CharField(max_length=16, choices=Plan.choices, default=Plan.BASIC)
    status = models.CharField(max_length=16, choices=Status.choices, default=Status.ACTIVE)
    started_at = models.DateTimeField(auto_now_add=True)
    cancelled_at = models.DateTimeField(null=True, blank=True)


class MembershipPayment(models.Model):
    membership = models.ForeignKey(Membership, on_delete=models.CASCADE, related_name='payments')
    amount = models.PositiveIntegerField(default=0)
    paid_at = models.DateTimeField(auto_now_add=True)
