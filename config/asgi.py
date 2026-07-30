"""
ASGI config for HackMin project.

Channels ProtocolTypeRouter 를 사용해 HTTP 와 WebSocket 을 동시에 처리한다.
daphne 이 이 모듈의 ``application`` 을 직접 실행한다.
"""

import os

from channels.routing import ProtocolTypeRouter, URLRouter
from django.core.asgi import get_asgi_application

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'config.settings')

# Django ASGI application (must be called before importing consumers)
django_asgi_app = get_asgi_application()

from orders.middleware import JWTAuthMiddleware  # noqa: E402
from orders.routing import websocket_urlpatterns  # noqa: E402

application = ProtocolTypeRouter({
    'http': django_asgi_app,
    'websocket': JWTAuthMiddleware(
        URLRouter(websocket_urlpatterns)
    ),
})
