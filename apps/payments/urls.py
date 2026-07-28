from django.urls import path

from . import views

app_name = 'payments'

urlpatterns = [
    path('payments', views.create_payment, name='create'),
    path('payments/<int:pk>', views.payment_detail, name='detail'),
    path('payments/<int:pk>/cancel', views.cancel_payment, name='cancel'),
    path('payments/<int:pk>/refund', views.refund_payment, name='refund'),

    # [훈련용 취약] 3단계 결제 워크플로우 2·3단계 (금액 변조 실습)
    path('payment', views.payment_popup, name='payment-popup'),
    path('paysuccess', views.pay_success, name='pay-success'),
]
