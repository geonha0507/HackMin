"""web_bff URL 설정.

경로와 URL 이름을 기존 apps/web 과 동일하게 유지한다. 템플릿의
{% url 'web:owner_dashboard' %} 같은 태그를 그대로 쓰기 위해서다.
"""

from django.http import JsonResponse
from django.urls import include, path

from . import views

# 네임스페이스를 'web' 으로 고정한다. 템플릿의 {% url 'web:...' %} 가
# apps/web 과 동일하게 동작하도록 하기 위해서다.
web_patterns = ([
    path('login', views.login_view, name='login'),
    path('logout', views.logout_view, name='logout'),
    path('signup', views.signup_view, name='signup'),
    path('check-duplicate', views.check_duplicate, name='check_duplicate'),
    path('owner/', views.dashboard, name='owner_dashboard'),
    path('owner/orders', views.order_list, name='owner_orders'),
    path('owner/orders/<int:pk>', views.order_detail, name='owner_order_detail'),
    path('owner/products', views.product_list, name='owner_products'),
    path('owner/products/new', views.product_form, name='owner_product_new'),
    path('owner/products/<int:pk>/edit', views.product_form, name='owner_product_edit'),
    path('owner/products/<int:pk>/delete', views.product_delete, name='owner_product_delete'),
    path('owner/categories', views.category_list, name='owner_categories'),
    path('owner/sales', views.sales, name='owner_sales'),
    path('owner/sales/report/<int:pk>', views.sales_report_download, name='owner_sales_report'),
    path('owner/reviews', views.review_list, name='owner_reviews'),
    path('owner/reviews/<int:pk>/delete', views.review_delete, name='owner_review_delete'),
    path('me', views.mypage, name='mypage'),
    path('me/password', views.password_change, name='password_change'),
    path('me/withdraw', views.withdraw, name='withdraw'),
    path('me/restaurant', views.my_restaurant, name='my_restaurant'),
    path('me/restaurant/add', views.restaurant_add, name='restaurant_add'),
    path('me/restaurant/image', views.restaurant_image_upload, name='restaurant_image_upload'),
    path('me/restaurant/closed-dates/add', views.closed_date_add, name='closed_date_add'),
    path('me/restaurant/closed-dates/<int:pk>/delete', views.closed_date_delete, name='closed_date_delete'),
    path('me/restaurant/regular-closed-days', views.regular_closed_days_update, name='regular_closed_days_update'),
    path('me/restaurant/notices/add', views.notice_add, name='notice_add'),
    path('me/restaurant/notices/<int:pk>/delete', views.notice_delete, name='notice_delete'),
], 'web')


def health(_request):
    return JsonResponse({'status': 'ok', 'service': 'hackmin-web-bff'})


urlpatterns = [
    path('web/health', health),
    path('web/', include(web_patterns)),
]
