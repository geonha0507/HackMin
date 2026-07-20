from django.urls import path

from chatbot import views as chatbot_views
from . import views

app_name = 'promotions'

urlpatterns = [
    # Coupons
    path('coupons', views.coupon_list, name='coupon-list'),
    path('coupons/register', views.register_coupon, name='coupon-register'),
    path('coupons/<int:pk>/download', views.download_coupon, name='coupon-download'),

    # Favorites
    path('favorites', views.FavoriteListCreateView.as_view(), name='favorites'),
    path('favorites/<int:pk>', views.delete_favorite, name='favorite-delete'),

    # Chatbot
    path('chatbot/message', chatbot_views.chatbot_message, name='chatbot-message'),

    # Membership
    path('membership/subscribe', views.membership_subscribe, name='membership-subscribe'),
    path('membership/cancel', views.membership_cancel, name='membership-cancel'),
    path('membership/benefits', views.membership_benefits, name='membership-benefits'),
    path('membership/payments', views.membership_payments, name='membership-payments'),
    path('membership/points', views.membership_points, name='membership-points'),

    # /me (spec section 2)
    path('me/coupons', views.MyCouponListView.as_view(), name='my-coupons'),
    path('me/membership', views.my_membership, name='my-membership'),
]
