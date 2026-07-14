from django.urls import path

from . import views

app_name = 'carts'

urlpatterns = [
    path('cart', views.cart_detail, name='detail'),
    path('cart/items', views.add_item, name='item-add'),
    path('cart/items/<int:pk>', views.item_detail, name='item-detail'),
    path('cart/coupon', views.apply_coupon, name='coupon'),
    path('cart/summary', views.cart_summary, name='summary'),
]
