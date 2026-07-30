from django.urls import path

from . import views

app_name = 'downloads'

urlpatterns = [
    path('downloads/receipt/<int:order_id>', views.receipt, name='receipt'),
    path('downloads/sales-report/<int:pk>', views.sales_report, name='sales-report'),
    path('downloads/business-license/<int:pk>', views.business_license, name='business-license'),
    path('downloads/order-history/<int:pk>', views.order_history, name='order-history'),
    path('downloads/attachment/<int:pk>', views.attachment, name='attachment'),
    path('downloads/notice/<int:pk>/attachment', views.notice_attachment, name='notice-attachment'),
]
