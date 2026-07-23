from django.urls import path

from . import views

app_name = 'admin_web'

urlpatterns = [
    # 인증 (admin 전용)
    path('login', views.login_view, name='login'),
    path('logout', views.logout_view, name='logout'),

    # 관리자 화면 (apps/web/urls.py 의 '# 관리자' 섹션 그대로 이동)
    path('', views.dashboard, name='admin_dashboard'),
    path('users', views.user_list, name='admin_users'),
    path('users/<int:pk>', views.user_detail, name='admin_user_detail'),
    path('owners', views.owner_list, name='admin_owners'),
    path('owners/<int:pk>/status', views.owner_status, name='admin_owner_status'),
    path('withdrawals', views.withdrawal_requests, name='admin_withdrawals'),
    path('withdrawals/<int:pk>/decide', views.withdrawal_decide, name='admin_withdrawal_decide'),
    path('restaurant-edits', views.restaurant_edit_requests, name='admin_restaurant_edits'),
    path('restaurant-edits/<int:pk>/decide', views.restaurant_edit_decide, name='admin_restaurant_edit_decide'),
    path('orders', views.order_list, name='admin_orders'),
    path('payments', views.payment_list, name='admin_payments'),
    path('notices', views.notice_list, name='admin_notices'),
    path('notices/new', views.notice_create, name='admin_notice_create'),
    path('notices/<int:pk>/edit', views.notice_edit, name='admin_notice_edit'),
    path('notices/<int:pk>/delete', views.notice_delete, name='admin_notice_delete'),
    path('store', views.store_list, name='admin_store'),
    path('store/<int:pk>/decide', views.store_decide, name='admin_store_decide'),
]
