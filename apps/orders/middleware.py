"""WebSocket JWT 인증 미들웨어.

WebSocket 은 HTTP 헤더를 커스텀할 수 없으므로, 연결 시 query string 으로
JWT access token 을 전달받아 인증한다.

  ws://host/ws/orders/42/status/?token=<access_token>
"""

import logging
from urllib.parse import parse_qs

from channels.db import database_sync_to_async
from channels.middleware import BaseMiddleware
from django.contrib.auth import get_user_model
from django.contrib.auth.models import AnonymousUser
from rest_framework_simplejwt.tokens import AccessToken
from rest_framework_simplejwt.exceptions import TokenError

logger = logging.getLogger(__name__)
User = get_user_model()


@database_sync_to_async
def _get_user(token_str):
    """토큰 문자열에서 사용자를 찾는다. 실패 시 AnonymousUser 반환."""
    try:
        validated = AccessToken(token_str)
        user_id = validated['user_id']
        return User.objects.get(pk=user_id)
    except (TokenError, User.DoesNotExist, KeyError) as exc:
        logger.debug('WS auth failed: %s', exc)
        return AnonymousUser()


class JWTAuthMiddleware(BaseMiddleware):
    """query string ``token`` 파라미터에서 JWT 를 꺼내 scope['user'] 에 설정."""

    async def __call__(self, scope, receive, send):
        qs = parse_qs(scope.get('query_string', b'').decode('utf-8'))
        token_list = qs.get('token', [])
        if token_list:
            scope['user'] = await _get_user(token_list[0])
        else:
            scope['user'] = AnonymousUser()
        return await super().__call__(scope, receive, send)
