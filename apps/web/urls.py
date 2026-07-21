from django.urls import path

from .views import account, admin, auth, owner, restaurant

app_name = 'web'

urlpatterns = [
    # 인증
    path('login', auth.login_view, name='login'),
    path('signup', auth.signup_view, name='signup'),
    path('logout', auth.logout_view, name='logout'),

    # 마이페이지
    path('me', account.mypage, name='mypage'),
    path('me/password', account.password_change, name='password_change'),
    path('me/withdraw', account.withdraw, name='withdraw'),
    path('me/restaurant', restaurant.my_restaurant, name='my_restaurant'),
    path('me/restaurant/add', restaurant.add_restaurant, name='restaurant_add'),
    path('me/restaurant/closed-dates/add', restaurant.closed_date_add, name='closed_date_add'),
    path('me/restaurant/closed-dates/<int:pk>/delete', restaurant.closed_date_delete, name='closed_date_delete'),
    path('me/restaurant/regular-closed-days', restaurant.regular_closed_days_update, name='regular_closed_days_update'),

    # 점주
    path('owner/', owner.dashboard, name='owner_dashboard'),
    path('owner/orders', owner.order_list, name='owner_orders'),
    path('owner/orders/<int:pk>', owner.order_detail, name='owner_order_detail'),
    path('owner/products', owner.product_list, name='owner_products'),
    path('owner/products/new', owner.product_form, name='owner_product_new'),
    path('owner/products/<int:pk>/edit', owner.product_form, name='owner_product_edit'),
    path('owner/products/<int:pk>/delete', owner.product_delete, name='owner_product_delete'),
    path('owner/categories', owner.category_list, name='owner_categories'),
    path('owner/sales', owner.sales, name='owner_sales'),
    path('owner/reviews', owner.review_list, name='owner_reviews'),

    # 관리자
    path('admin/', admin.dashboard, name='admin_dashboard'),
    path('admin/users', admin.user_list, name='admin_users'),
    path('admin/users/<int:pk>', admin.user_detail, name='admin_user_detail'),
    path('admin/owners', admin.owner_list, name='admin_owners'),
    path('admin/owners/<int:pk>/status', admin.owner_status, name='admin_owner_status'),
    path('admin/withdrawals', admin.withdrawal_requests, name='admin_withdrawals'),
    path('admin/withdrawals/<int:pk>/decide', admin.withdrawal_decide, name='admin_withdrawal_decide'),
    path('admin/restaurant-edits', admin.restaurant_edit_requests, name='admin_restaurant_edits'),
    path('admin/restaurant-edits/<int:pk>/decide', admin.restaurant_edit_decide, name='admin_restaurant_edit_decide'),
    path('admin/orders', admin.order_list, name='admin_orders'),
    path('admin/payments', admin.payment_list, name='admin_payments'),
    path(
        'admin/store',
        admin.store_list,
        name='admin_store',
    ),
    path(
        'admin/store/<int:pk>/decide',
        admin.store_decide,
        name='admin_store_decide',
    ),
]
