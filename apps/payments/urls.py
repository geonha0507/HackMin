from django.urls import path

from . import views

app_name = 'payments'

urlpatterns = [
    path('payments', views.create_payment, name='create'),
    path('payments/<int:pk>', views.payment_detail, name='detail'),
    path('payments/<int:pk>/cancel', views.cancel_payment, name='cancel'),
    path('payments/<int:pk>/refund', views.refund_payment, name='refund'),
]
