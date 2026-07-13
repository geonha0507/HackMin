from django.conf import settings
from django.db import models


class Cart(models.Model):
    user = models.OneToOneField(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='cart',
    )
    restaurant = models.ForeignKey(
        'restaurants.Restaurant', on_delete=models.SET_NULL, null=True, blank=True,
    )
    coupon = models.ForeignKey(
        'promotions.Coupon', on_delete=models.SET_NULL, null=True, blank=True,
    )
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return f'Cart(user={self.user_id})'


class CartItem(models.Model):
    cart = models.ForeignKey(Cart, on_delete=models.CASCADE, related_name='items')
    menu = models.ForeignKey('restaurants.Menu', on_delete=models.CASCADE)
    quantity = models.PositiveIntegerField(default=1)
    # List of selected MenuOption ids, snapshotted for pricing.
    options = models.JSONField(default=list, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f'{self.menu_id} x{self.quantity}'
