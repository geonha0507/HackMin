from django.urls import path

from . import views

app_name = 'inquiries'

urlpatterns = [
    path('inquiries', views.InquiryListCreateView.as_view(), name='list_create'),
    path('inquiries/<int:pk>', views.InquiryDetailView.as_view(), name='detail'),
    path('inquiries/<int:pk>/images', views.upload_inquiry_image, name='images'),
]
