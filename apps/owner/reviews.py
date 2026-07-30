"""Owner review management (/api/v1/owner/reviews)."""

from django.utils import timezone
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from common.exceptions import error_response
from common.permissions import IsOwner
from reviews.models import Review, ReviewReply

from .serializers import OwnerReviewSerializer


def _review_scope(request):
    qs = Review.objects.filter(restaurant__owner=request.user)

    restaurant_id = request.query_params.get('restaurant_id')
    if restaurant_id:
        try:
            rid = int(restaurant_id)
        except (TypeError, ValueError):
            rid = None
        if rid is not None:
            # 소유하지 않은 매장 id 는 그대로 필터에 넣어도 위 조건 때문에
            # 결과가 비므로 안전하다 (IDOR 로 남의 리뷰가 새지 않는다).
            qs = qs.filter(restaurant_id=rid)
    return qs


@api_view(['GET'])
@permission_classes([IsOwner])
def owner_review_list(request):
    """본인 매장 리뷰 목록. ?restaurant_id=N 으로 매장 필터."""
    reviews = (
        _review_scope(request)
        .select_related('user', 'restaurant')
        .prefetch_related('images', 'reply')
        .order_by('-created_at')
    )
    return Response({'results': OwnerReviewSerializer(reviews, many=True).data})


@api_view(['POST'])
@permission_classes([IsOwner])
def owner_review_reply(request, pk):
    """리뷰 답변 작성. 본인 매장 리뷰만 답변 가능."""
    review = _review_scope(request).filter(pk=pk).first()
    if not review:
        return error_response('not_found', '리뷰를 찾을 수 없습니다.', 404)
    content = (request.data.get('content') or '').strip()
    if not content:
        return error_response('bad_request', '답변 내용이 필요합니다.', 400)

    reply, _ = ReviewReply.objects.update_or_create(
        review=review, defaults={'owner': request.user, 'content': content},
    )
    return Response(
        {'id': reply.id, 'review': review.id, 'content': reply.content,
         'created_at': reply.created_at},
        status=201,
    )


@api_view(['POST'])
@permission_classes([IsOwner])
def owner_review_delete(request, pk):
    """점주가 본인 매장 리뷰를 사유와 함께 소프트 삭제한다.

    고객이 쓰는 DELETE /reviews/<pk> 는 작성자 본인만 호출할 수 있고 하드
    삭제다. 점주의 삭제는 사유가 남고 되돌릴 수 있어야 하므로 별도 경로로 둔다.
    이 로직은 그동안 apps/web 화면에만 있어 API 로는 불가능했다.
    """
    review = _review_scope(request).filter(pk=pk).first()
    if not review:
        return error_response('not_found', '리뷰를 찾을 수 없습니다.', 404)

    reason = (request.data.get('reason') or '').strip()
    if not reason:
        return error_response('bad_request', '삭제 사유를 입력하세요.', 400)
    if review.is_deleted:
        return error_response('already_deleted', '이미 삭제된 리뷰입니다.', 409)

    review.is_deleted = True
    review.delete_reason = reason
    review.deleted_at = timezone.now()
    review.deleted_by = request.user
    review.save(update_fields=[
        'is_deleted', 'delete_reason', 'deleted_at', 'deleted_by', 'updated_at',
    ])

    # 삭제된 리뷰는 평점 계산에서 빠지므로 매장 평점을 다시 계산한다.
    from reviews.views import _recompute_restaurant_rating
    _recompute_restaurant_rating(review.restaurant)

    return Response(OwnerReviewSerializer(review).data)
