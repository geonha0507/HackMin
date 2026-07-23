"""1:1 문의 (/api/v1/inquiries)."""

import os

from rest_framework import generics
from rest_framework.decorators import api_view, parser_classes, permission_classes
from rest_framework.parsers import FormParser, MultiPartParser
from rest_framework.response import Response

from common.exceptions import error_response
from common.permissions import IsCustomer
from .models import Inquiry, InquiryImage
from .serializers import InquiryCreateSerializer, InquirySerializer

_ALLOWED_IMAGE_EXT = {'.jpg', '.jpeg', '.png', '.gif', '.webp'}
_MAX_IMAGE_BYTES = 5 * 1024 * 1024
_MAX_IMAGES = 5


class InquiryListCreateView(generics.ListCreateAPIView):
    """본인이 작성한 1:1 문의 목록 조회(작성일 오름차순) 및 문의 작성."""
    permission_classes = [IsCustomer]

    def get_queryset(self):
        return Inquiry.objects.filter(user=self.request.user)

    def get_serializer_class(self):
        return InquiryCreateSerializer if self.request.method == 'POST' else InquirySerializer

    def create(self, request, *args, **kwargs):
        serializer = InquiryCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        inquiry = Inquiry.objects.create(user=request.user, **serializer.validated_data)
        return Response(InquirySerializer(inquiry).data, status=201)


class InquiryDetailView(generics.RetrieveUpdateDestroyAPIView):
    """1:1 문의 상세 조회 / 수정 / 삭제. 본인 글만 접근 가능."""
    permission_classes = [IsCustomer]
    serializer_class = InquirySerializer

    def get_queryset(self):
        return Inquiry.objects.filter(user=self.request.user)

    def update(self, request, *args, **kwargs):
        inquiry = self.get_object()
        serializer = InquiryCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        for field, value in serializer.validated_data.items():
            setattr(inquiry, field, value)
        inquiry.save()
        return Response(InquirySerializer(inquiry).data)


@api_view(['POST'])
@permission_classes([IsCustomer])
@parser_classes([MultiPartParser, FormParser])
def upload_inquiry_image(request, pk):
    """1:1 문의 이미지 업로드. 이미지 확장자 화이트리스트 + 크기/장수 제한을 적용한다."""
    inquiry = Inquiry.objects.filter(pk=pk, user=request.user).first()
    if not inquiry:
        return error_response('not_found', '문의를 찾을 수 없습니다.', 404)

    if inquiry.images.count() >= _MAX_IMAGES:
        return error_response(
            'too_many_images', f'이미지는 최대 {_MAX_IMAGES}장까지 업로드할 수 있습니다.', 400,
        )

    upload = request.FILES.get('image')
    if not upload:
        return error_response('bad_request', 'image 파일이 필요합니다.', 400)

    ext = os.path.splitext(upload.name)[1].lower()
    if ext not in _ALLOWED_IMAGE_EXT:
        return error_response('invalid_file_type', '허용되지 않는 파일 형식입니다.', 400)
    if upload.size > _MAX_IMAGE_BYTES:
        return error_response('file_too_large', '이미지 크기는 5MB 이하만 허용됩니다.', 400)

    image = InquiryImage.objects.create(inquiry=inquiry, image=upload)
    return Response({'id': image.id, 'image': image.image.url}, status=201)
