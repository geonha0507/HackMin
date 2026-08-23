from django.urls import path

from . import views

app_name = 'orders'

urlpatterns = [
    path('orders', views.create_order, name='create'),
    # [방어 ⑩] 수령확인 서명용 공개키 등록 (int pk 라우트보다 먼저 둔다)
    path('orders/receipt-key', views.register_receipt_key, name='receipt-key'),
    path('orders/<int:pk>', views.order_detail, name='detail'),
    path('orders/<int:pk>/status', views.order_status, name='status'),
    path('orders/<int:pk>/confirm-receipt', views.confirm_receipt, name='confirm-receipt'),
    path('orders/<int:pk>/cancel', views.cancel_order, name='cancel'),
    path('orders/<int:pk>/reorder', views.reorder, name='reorder'),

    # /me/orders (spec section 2)
    path('me/orders', views.MyOrderListView.as_view(), name='my-orders'),
]
