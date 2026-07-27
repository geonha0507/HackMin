from django.urls import path

from . import moderation, notices, views

app_name = 'adminpanel'

urlpatterns = [
    path('admin/users', views.user_list, name='users'),
    path('admin/users/<int:pk>', views.user_detail, name='user-detail'),
    path('admin/users/<int:pk>/status', views.user_status, name='user-status'),
    path('admin/owners', views.owner_list, name='owners'),
    path('admin/owners/<int:pk>/status', views.owner_status, name='owner-status'),
    path('admin/orders', views.order_list, name='orders'),
    path('admin/payments', views.payment_list, name='payments'),

    # 승인·심사 (apps/admin_web 화면에만 있던 흐름)
    path('admin/dashboard', moderation.admin_dashboard, name='dashboard'),
    path('admin/dashboard/baseline', moderation.admin_dashboard_baseline, name='dashboard-baseline'),
    path('admin/withdrawals', moderation.admin_withdrawal_list, name='withdrawals'),
    path('admin/withdrawals/<int:pk>/decide', moderation.admin_withdrawal_decide, name='withdrawal-decide'),
    path('admin/restaurant-edits', moderation.admin_restaurant_edit_list, name='restaurant-edits'),
    path('admin/restaurant-edits/<int:pk>/decide', moderation.admin_restaurant_edit_decide, name='restaurant-edit-decide'),
    path('admin/stores', moderation.admin_store_list, name='stores'),
    path('admin/stores/<int:pk>/decide', moderation.admin_store_decide, name='store-decide'),

    # Notices
    path('notices', notices.notice_list, name='notices'),
    path('notices/<int:pk>', notices.notice_detail, name='notice-detail'),
    path('admin/notices', notices.admin_notice_list_create, name='admin-notices'),
    path('admin/notices/<int:pk>', notices.admin_notice_detail, name='admin-notice-detail'),
]
