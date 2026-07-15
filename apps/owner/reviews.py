"""Owner review management (/api/v1/owner/reviews)."""

import bleach
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from common.exceptions import error_response
from common.permissions import IsOwner
from reviews.models import Review, ReviewReply
from reviews.serializers import ReviewSerializer


def _review_scope(request):
    """항상 본인 매장 리뷰로 한정 (Secure 고정, Vulnerable 분기 임시 제거)."""
    return Review.objects.filter(restaurant__owner=request.user)


@api_view(['GET'])
@permission_classes([IsOwner])
def owner_review_list(request):
    reviews = _review_scope(request).select_related('user').prefetch_related('images').order_by('-created_at')
    return Response({'results': ReviewSerializer(reviews, many=True).data})


@api_view(['POST'])
@permission_classes([IsOwner])
def owner_review_reply(request, pk):
    """리뷰 답변 작성. 본인 매장 리뷰만 답변 가능, 내용은 HTML 태그 제거 후 저장 (Secure 고정)."""
    review = _review_scope(request).filter(pk=pk).first()
    if not review:
        return error_response('not_found', '리뷰를 찾을 수 없습니다.', 404)
    content = request.data.get('content', '')
    if not content:
        return error_response('bad_request', '답변 내용이 필요합니다.', 400)

    content = bleach.clean(content, tags=[], attributes={}, strip=True)

    reply, _ = ReviewReply.objects.update_or_create(
        review=review, defaults={'owner': request.user, 'content': content},
    )
    return Response(
        {'id': reply.id, 'review': review.id, 'content': reply.content,
         'created_at': reply.created_at},
        status=201,
    )
