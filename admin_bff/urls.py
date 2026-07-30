"""admin_bff URL 설정.

경로와 URL 이름을 기존 apps/admin_web 과 동일하게 유지한다. 템플릿의
{% url 'admin_web:...' %} 태그를 그대로 쓰기 위해서다.
"""

from django.http import JsonResponse
from django.urls import include, path

from . import views

admin_patterns = ([
    path('login', views.login_view, name='login'),
    path('logout', views.logout_view, name='logout'),

    path('', views.dashboard, name='admin_dashboard'),
    path('users', views.user_list, name='admin_users'),
    path('users/<int:pk>', views.user_detail, name='admin_user_detail'),
    path('owners', views.owner_list, name='admin_owners'),
    path('owners/<int:pk>/status', views.owner_status, name='admin_owner_status'),
    path('withdrawals', views.withdrawal_requests, name='admin_withdrawals'),
    path('withdrawals/<int:pk>/decide', views.withdrawal_decide, name='admin_withdrawal_decide'),
    path('restaurant-edits', views.restaurant_edit_requests, name='admin_restaurant_edits'),
    path('restaurant-edits/<int:pk>/decide', views.restaurant_edit_decide,
         name='admin_restaurant_edit_decide'),
    path('orders', views.order_list, name='admin_orders'),
    path('payments', views.payment_list, name='admin_payments'),
    path('notices', views.notice_list, name='admin_notices'),
    path('notices/new', views.notice_create, name='admin_notice_create'),
    path('notices/<int:pk>/edit', views.notice_edit, name='admin_notice_edit'),
    path('notices/<int:pk>/delete', views.notice_delete, name='admin_notice_delete'),
    path('store', views.store_list, name='admin_store'),
    path('store/<int:pk>/decide', views.store_decide, name='admin_store_decide'),

    # 1:1 문의 (고객지원) — 상세 화면이 저장형 XSS 발동점
    path('inquiries', views.inquiry_list, name='admin_inquiries'),
    path('inquiries/<int:pk>', views.inquiry_detail, name='admin_inquiry_detail'),
], 'admin_web')


def health(_request):
    return JsonResponse({'status': 'ok', 'service': 'hackmin-admin-bff'})


urlpatterns = [
    path('admin-web/health', health),
    path('admin-web/', include(admin_patterns)),
]
