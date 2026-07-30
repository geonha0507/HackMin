"""주문 상태 실시간 WebSocket Consumer.

고객이 ws/orders/<order_id>/status/ 에 접속하면 해당 주문의 상태 변경을
실시간으로 수신한다. 점주가 상태를 변경하면 channel layer group_send 를
통해 이 consumer 로 전달된다.
"""

import json
import logging

from channels.generic.websocket import AsyncJsonWebsocketConsumer
from channels.db import database_sync_to_async

logger = logging.getLogger(__name__)


class OrderStatusConsumer(AsyncJsonWebsocketConsumer):
    """주문 상태 변경을 실시간 푸시하는 WebSocket consumer."""

    async def connect(self):
        self.order_id = self.scope['url_route']['kwargs']['order_id']
        self.group_name = f'order_{self.order_id}'

        user = self.scope.get('user')
        if not user or not user.is_authenticated:
            await self.close(code=4001)
            return

        # 본인 주문 또는 본인 매장 주문인지 확인
        has_access = await self._check_access(user, self.order_id)
        if not has_access:
            await self.close(code=4003)
            return

        await self.channel_layer.group_add(self.group_name, self.channel_name)
        await self.accept()
        logger.info('WS connected: user=%s order=%s', user.id, self.order_id)

    async def disconnect(self, close_code):
        if hasattr(self, 'group_name'):
            await self.channel_layer.group_discard(
                self.group_name, self.channel_name
            )
        logger.info('WS disconnected: order=%s code=%s',
                     getattr(self, 'order_id', '?'), close_code)

    async def receive_json(self, content, **kwargs):
        """클라이언트→서버 메시지는 현재 사용하지 않는다 (ping/pong 정도)."""
        msg_type = content.get('type')
        if msg_type == 'ping':
            await self.send_json({'type': 'pong'})

    # ----- channel layer 에서 호출되는 핸들러 -----

    async def order_status_update(self, event):
        """점주가 상태를 변경하면 services.py 에서 group_send 로 전달된다."""
        await self.send_json({
            'type': 'order.status_update',
            'order_id': event['order_id'],
            'status': event['status'],
            'status_display': event.get('status_display', ''),
        })

    # ----- helpers -----

    @database_sync_to_async
    def _check_access(self, user, order_id):
        from orders.models import Order
        from django.db.models import Q
        return Order.objects.filter(
            pk=order_id
        ).filter(
            Q(user=user) | Q(restaurant__owner=user)
        ).exists()
