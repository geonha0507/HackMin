from django.urls import path

from . import views

app_name = 'inquiries'

urlpatterns = [
    path('inquiries', views.InquiryListCreateView.as_view(), name='list_create'),
    path('inquiries/<int:pk>', views.InquiryDetailView.as_view(), name='detail'),
    path('inquiries/<int:pk>/images', views.upload_inquiry_image, name='images'),

    # 🎯 관리자 조회 (admin_bff 가 호출) — /api/v1/admin/inquiries
    path('admin/inquiries', views.admin_inquiry_list, name='admin_list'),
    path('admin/inquiries/<int:pk>', views.admin_inquiry_detail, name='admin_detail'),
    path('admin/inquiries/<int:pk>/reply', views.admin_inquiry_reply, name='admin_reply'),
]
