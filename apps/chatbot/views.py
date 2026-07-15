"""Chatbot endpoint (/api/v1/chatbot/message)."""

from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from common.exceptions import error_response
from common.permissions import IsCustomer
from restaurants.models import Restaurant


def _recommend():
    top = Restaurant.objects.filter(is_open=True).order_by('-rating')[:3]
    return [{'id': r.id, 'name': r.name, 'rating': r.rating} for r in top]


@api_view(['POST'])
@permission_classes([IsCustomer])
def chatbot_message(request):
    """챗봇 메시지. 메시지를 평문으로 취급하고 규칙 기반으로만 응답한다."""
    message = request.data.get('message', '')
    if not message:
        return error_response('bad_request', 'message가 필요합니다.', 400)

    lowered = message.lower()
    if any(k in lowered for k in ['추천', 'recommend', '맛집']):
        reply = '평점이 높은 음식점을 추천드려요.'
        recommendations = _recommend()
    else:
        reply = '무엇을 도와드릴까요? "맛집 추천"이라고 입력해 보세요.'
        recommendations = []

    return Response({
        'reply': f'"{message}" 라고 하셨네요. {reply}',
        'recommendations': recommendations,
    })
