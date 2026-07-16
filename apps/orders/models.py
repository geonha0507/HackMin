import uuid

from django.conf import settings
from django.db import models


def generate_order_number():
    return uuid.uuid4().hex[:16].upper()


class Order(models.Model):
    class Status(models.TextChoices):
        PENDING = 'pending', 'Pending payment'
        PLACED = 'placed', 'Placed'
        ACCEPTED = 'accepted', 'Accepted'
        REJECTED = 'rejected', 'Rejected'
        COOKING = 'cooking', 'Cooking'
        COOKED = 'cooked', 'Cooked'
        DELIVERING = 'delivering', 'Delivering'
        DELIVERED = 'delivered', 'Delivered'
        CANCELLED = 'cancelled', 'Cancelled'

    order_number = models.CharField(max_length=32, unique=True, default=generate_order_number)
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='orders',
    )
    restaurant = models.ForeignKey(
        'restaurants.Restaurant', on_delete=models.SET_NULL, null=True, related_name='orders',
    )
    coupon = models.ForeignKey(
        'promotions.Coupon', on_delete=models.SET_NULL, null=True, blank=True, related_name='orders',
    )
    status = models.CharField(max_length=16, choices=Status.choices, default=Status.PENDING)

    subtotal = models.PositiveIntegerField(default=0)
    delivery_fee = models.PositiveIntegerField(default=0)
    discount = models.PositiveIntegerField(default=0)
    total = models.PositiveIntegerField(default=0)

    # Snapshot of the delivery address at order time.
    address = models.CharField(max_length=255, blank=True)
    address_detail = models.CharField(max_length=255, blank=True)
    request_note = models.TextField(blank=True)

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return self.order_number


class OrderItem(models.Model):
    order = models.ForeignKey(Order, on_delete=models.CASCADE, related_name='items')
    menu = models.ForeignKey('restaurants.Menu', on_delete=models.SET_NULL, null=True)
    menu_name = models.CharField(max_length=128)     # snapshot
    unit_price = models.PositiveIntegerField(default=0)
    quantity = models.PositiveIntegerField(default=1)
    options = models.JSONField(default=list, blank=True)
    line_total = models.PositiveIntegerField(default=0)

    def __str__(self):
        return f'{self.menu_name} x{self.quantity}'
