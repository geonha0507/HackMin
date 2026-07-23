from django.urls import path

from .views import account, auth, owner, restaurant

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
    path('me/restaurant/image', restaurant.restaurant_image_upload, name='restaurant_image_upload'),
    path('me/restaurant/notices/add', restaurant.notice_add, name='notice_add'),
    path('me/restaurant/notices/<int:pk>/delete', restaurant.notice_delete, name='notice_delete'),

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

    # 관리자 화면은 apps/admin_web 앱(별도 컨테이너)으로 이동함.
    # (config/urls_admin.py 에서 'admin_web.urls' 로 include)
]
