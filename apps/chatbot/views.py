"""Chatbot endpoint (/api/v1/chatbot/message)."""

from django.template import Context, Template
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from common.exceptions import error_response
from common.mode import is_vulnerable
from common.permissions import IsCustomer
from restaurants.models import Restaurant


def _recommend():
    top = Restaurant.objects.filter(is_open=True).order_by('-rating')[:3]
    return [{'id': r.id, 'name': r.name, 'rating': r.rating} for r in top]


@api_view(['POST'])
@permission_classes([IsCustomer])
def chatbot_message(request):
    """🎯 챗봇 메시지.

    Vulnerable 모드: 사용자 메시지를 Django 템플릿으로 렌더링(SSTI) →
    예) "{{ user.username }}" 또는 "{% ... %}" 삽입 시 서버 컨텍스트가 평가된다.
    Secure 모드: 메시지를 평문으로 취급하고 규칙 기반으로만 응답한다.
    """
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

    if is_vulnerable(request):
        # VULNERABLE: 사용자 입력을 템플릿 소스로 렌더링(SSTI).
        rendered = Template(f'안녕하세요 {message} 님, {reply}').render(
            Context({'user': request.user, 'request': request})
        )
        return Response({'reply': rendered, 'recommendations': recommendations})

    return Response({
        'reply': f'"{message}" 라고 하셨네요. {reply}',
        'recommendations': recommendations,
    })
