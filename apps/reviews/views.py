"""Review endpoints (/api/v1/reviews) and /api/v1/me/reviews."""

import os

from rest_framework import generics
from rest_framework.decorators import api_view, parser_classes, permission_classes
from rest_framework.parsers import FormParser, MultiPartParser
from rest_framework.response import Response

from common.exceptions import error_response
from common.mode import is_vulnerable
from common.permissions import IsCustomer
from orders.models import Order
from restaurants.models import Restaurant
from .models import Review, ReviewImage
from .serializers import ReviewCreateSerializer, ReviewSerializer

_ALLOWED_IMAGE_EXT = {'.jpg', '.jpeg', '.png', '.gif', '.webp'}
_MAX_IMAGE_BYTES = 5 * 1024 * 1024


@api_view(['POST'])
@permission_classes([IsCustomer])
def create_review(request):
    """🎯 리뷰 작성.

    Vulnerable 모드: 해당 음식점에서 주문한 적이 없어도 리뷰 작성 허용,
    content를 정제 없이 저장(Stored XSS).
    Secure 모드: 본인의 완료 주문이 있어야 작성 가능.
    """
    serializer = ReviewCreateSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    data = serializer.validated_data

    restaurant = Restaurant.objects.filter(pk=data['restaurant']).first()
    if not restaurant:
        return error_response('not_found', '음식점을 찾을 수 없습니다.', 404)

    order = None
    if not is_vulnerable(request):
        order_qs = Order.objects.filter(
            user=request.user, restaurant=restaurant, status=Order.Status.DELIVERED,
        )
        if data.get('order'):
            order_qs = order_qs.filter(pk=data['order'])
        order = order_qs.first()
        if not order:
            return error_response(
                'not_eligible', '해당 음식점의 완료된 주문이 있어야 리뷰를 작성할 수 있습니다.', 403,
            )
    elif data.get('order'):
        order = Order.objects.filter(pk=data['order']).first()

    review = Review.objects.create(
        user=request.user,
        restaurant=restaurant,
        order=order,
        rating=data['rating'],
        content=data['content'],
    )
    return Response(ReviewSerializer(review).data, status=201)


@api_view(['PUT', 'DELETE'])
@permission_classes([IsCustomer])
def review_detail(request, pk):
    """🎯 리뷰 수정/삭제. Vulnerable 모드는 소유자 검증 없음(IDOR)."""
    if is_vulnerable(request):
        review = Review.objects.filter(pk=pk).first()
    else:
        review = Review.objects.filter(pk=pk, user=request.user).first()
    if not review:
        return error_response('not_found', '리뷰를 찾을 수 없습니다.', 404)

    if request.method == 'DELETE':
        review.delete()
        return Response(status=204)

    if 'rating' in request.data:
        review.rating = request.data['rating']
    if 'content' in request.data:
        review.content = request.data['content']
    review.save()
    return Response(ReviewSerializer(review).data)


@api_view(['POST'])
@permission_classes([IsCustomer])
@parser_classes([MultiPartParser, FormParser])
def upload_review_image(request, pk):
    """🎯 리뷰 이미지 업로드.

    Vulnerable 모드: 확장자·크기 검증 없이 업로드 허용(Unrestricted File Upload).
    Secure 모드: 이미지 확장자 화이트리스트 + 크기 제한.
    """
    if is_vulnerable(request):
        review = Review.objects.filter(pk=pk).first()
    else:
        review = Review.objects.filter(pk=pk, user=request.user).first()
    if not review:
        return error_response('not_found', '리뷰를 찾을 수 없습니다.', 404)

    upload = request.FILES.get('image')
    if not upload:
        return error_response('bad_request', 'image 파일이 필요합니다.', 400)

    if not is_vulnerable(request):
        ext = os.path.splitext(upload.name)[1].lower()
        if ext not in _ALLOWED_IMAGE_EXT:
            return error_response('invalid_file_type', '허용되지 않는 파일 형식입니다.', 400)
        if upload.size > _MAX_IMAGE_BYTES:
            return error_response('file_too_large', '이미지 크기는 5MB 이하만 허용됩니다.', 400)

    image = ReviewImage.objects.create(review=review, image=upload)
    return Response({'id': image.id, 'image': image.image.url}, status=201)


class MyReviewListView(generics.ListAPIView):
    """작성 리뷰 조회 (/me/reviews)."""
    serializer_class = ReviewSerializer
    permission_classes = [IsCustomer]

    def get_queryset(self):
        return Review.objects.filter(user=self.request.user).prefetch_related('images')
