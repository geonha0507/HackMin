from django.urls import path

from . import account, orders, products, restaurants, reviews, sales

app_name = 'owner'

urlpatterns = [
    # Account / 마이페이지
    path('owner/signup', account.owner_signup, name='signup'),
    path('owner/profile', account.owner_profile, name='profile'),
    path('owner/password', account.owner_password_change, name='password'),
    path('owner/withdrawal', account.owner_withdrawal, name='withdrawal'),
    path('owner/business-license', account.upload_business_license, name='business-license'),

    # Restaurants (점주 소유 매장) — web_bff 대시보드용
    path('owner/restaurants', restaurants.owner_restaurant_list, name='restaurants'),

    # Products
    path('owner/products', products.product_list_create, name='products'),
    path('owner/products/<int:pk>', products.product_detail, name='product-detail'),
    path('owner/products/<int:pk>/image', products.product_image, name='product-image'),
    path('owner/products/<int:pk>/status', products.product_status, name='product-status'),
    path('owner/products/<int:pk>/options', products.product_options, name='product-options'),
    path('owner/options/<int:pk>', products.option_detail, name='option-detail'),

    # Categories
    path('owner/categories', products.category_list_create, name='categories'),
    path('owner/categories/<int:pk>', products.category_detail, name='category-detail'),
    path('owner/categories/<int:pk>/move', products.category_move, name='category-move'),

    # Orders
    path('owner/orders', orders.owner_order_list, name='orders'),
    path('owner/orders/<int:pk>', orders.owner_order_detail, name='order-detail'),
    path('owner/orders/<int:pk>/accept', orders.accept_order, name='order-accept'),
    path('owner/orders/<int:pk>/reject', orders.reject_order, name='order-reject'),
    path('owner/orders/<int:pk>/status', orders.update_order_status, name='order-status'),
    path('owner/orders/<int:pk>/cancel', orders.cancel_order, name='order-cancel'),

    # Payments
    path('owner/payments', orders.owner_payment_list, name='payments'),
    path('owner/payments/<int:pk>', orders.owner_payment_detail, name='payment-detail'),
    path('owner/payments/<int:pk>/cancel', orders.owner_payment_cancel, name='payment-cancel'),
    path('owner/payments/<int:pk>/refund', orders.owner_payment_refund, name='payment-refund'),

    # Sales
    path('owner/sales', sales.sales_summary, name='sales'),
    path('owner/sales/by-menu', sales.sales_by_menu, name='sales-by-menu'),
    path('owner/sales/stats', sales.sales_stats, name='sales-stats'),
    path('owner/sales/export', sales.sales_export, name='sales-export'),

    # Reviews
    path('owner/reviews', reviews.owner_review_list, name='reviews'),
    path('owner/reviews/<int:pk>/reply', reviews.owner_review_reply, name='review-reply'),
    path('owner/reviews/<int:pk>/delete', reviews.owner_review_delete, name='review-delete'),
]
