from django.urls import path

from . import notices, views

app_name = 'adminpanel'

urlpatterns = [
    path('admin/users', views.user_list, name='users'),
    path('admin/users/<int:pk>', views.user_detail, name='user-detail'),
    path('admin/users/<int:pk>/status', views.user_status, name='user-status'),
    path('admin/owners', views.owner_list, name='owners'),
    path('admin/owners/<int:pk>/status', views.owner_status, name='owner-status'),
    path('admin/orders', views.order_list, name='orders'),
    path('admin/payments', views.payment_list, name='payments'),

    # Notices
    path('notices', notices.notice_list, name='notices'),
    path('notices/<int:pk>', notices.notice_detail, name='notice-detail'),
    path('admin/notices', notices.admin_notice_list_create, name='admin-notices'),
    path('admin/notices/<int:pk>', notices.admin_notice_detail, name='admin-notice-detail'),
]
