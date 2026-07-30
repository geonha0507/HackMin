"""WebSocket URL 라우팅."""

from django.urls import path

from . import consumers

websocket_urlpatterns = [
    path('ws/orders/<int:order_id>/status/', consumers.OrderStatusConsumer.as_asgi()),
]
