"""Owner account endpoints (/api/v1/owner/{signup,profile,business-license})."""

import os

from rest_framework import status
from rest_framework.decorators import api_view, parser_classes, permission_classes
from rest_framework.parsers import FormParser, MultiPartParser
from rest_framework.permissions import AllowAny
from rest_framework.response import Response

from common.exceptions import error_response
from common.permissions import IsOwner
from restaurants.models import Restaurant
from .serializers import OwnerPasswordChangeSerializer, OwnerProfileSerializer, OwnerSignupSerializer

_ALLOWED_DOC_EXT = {'.pdf', '.jpg', '.jpeg', '.png'}
_MAX_DOC_BYTES = 10 * 1024 * 1024


@api_view(['POST'])
@permission_classes([AllowAny])
def owner_signup(request):
    serializer = OwnerSignupSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    user = serializer.save()
    return Response(OwnerProfileSerializer(user).data, status=201)


@api_view(['GET', 'PUT', 'DELETE'])
@permission_classes([IsOwner])
def owner_profile(request):
    """점주 마이페이지: 정보 조회/수정, 탈퇴."""
    if request.method == 'GET':
        return Response(OwnerProfileSerializer(request.user).data)

    if request.method == 'PUT':
        serializer = OwnerProfileSerializer(request.user, data=request.data, partial=True)
        serializer.is_valid(raise_exception=True)
        serializer.save()
        return Response(serializer.data)

    # DELETE -> 탈퇴
    user = request.user
    user.status = user.Status.WITHDRAWN
    user.is_active = False
    user.save(update_fields=['status', 'is_active'])
    return Response(status=status.HTTP_204_NO_CONTENT)


@api_view(['PUT'])
@permission_classes([IsOwner])
def owner_password_change(request):
    """점주 비밀번호 변경."""
    serializer = OwnerPasswordChangeSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    user = request.user
    if not user.check_password(serializer.validated_data['old_password']):
        return error_response('invalid_password', '현재 비밀번호가 올바르지 않습니다.', 400)
    user.set_password(serializer.validated_data['new_password'])
    user.save(update_fields=['password'])
    return Response({'detail': '비밀번호가 변경되었습니다.'})


@api_view(['POST'])
@permission_classes([IsOwner])
@parser_classes([MultiPartParser, FormParser])
def upload_business_license(request):
    """사업자등록증 업로드. 문서 확장자 화이트리스트 + 크기 제한 적용."""
    upload = request.FILES.get('file')
    if not upload:
        return error_response('bad_request', 'file이 필요합니다.', 400)

    restaurant = Restaurant.objects.filter(owner=request.user).first()
    if not restaurant:
        restaurant = Restaurant.objects.create(owner=request.user, name=f'{request.user.username}의 매장')

    ext = os.path.splitext(upload.name)[1].lower()
    if ext not in _ALLOWED_DOC_EXT:
        return error_response('invalid_file_type', '허용되지 않는 파일 형식입니다.', 400)
    if upload.size > _MAX_DOC_BYTES:
        return error_response('file_too_large', '파일 크기는 10MB 이하만 허용됩니다.', 400)

    restaurant.business_license = upload
    restaurant.save(update_fields=['business_license'])
    return Response({'business_license': restaurant.business_license.url}, status=201)
