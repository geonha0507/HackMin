"""Owner account endpoints (/api/v1/owner/{signup,profile,business-license})."""

import os

from rest_framework.decorators import api_view, parser_classes, permission_classes
from rest_framework.parsers import FormParser, MultiPartParser
from rest_framework.permissions import AllowAny
from rest_framework.response import Response

from common.exceptions import error_response
from common.permissions import IsOwner
from restaurants.models import Restaurant
from .serializers import OwnerProfileSerializer, OwnerSignupSerializer

_ALLOWED_DOC_EXT = {'.pdf', '.jpg', '.jpeg', '.png'}
_MAX_DOC_BYTES = 10 * 1024 * 1024


@api_view(['POST'])
@permission_classes([AllowAny])
def owner_signup(request):
    serializer = OwnerSignupSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    user = serializer.save()
    return Response(OwnerProfileSerializer(user).data, status=201)


@api_view(['GET', 'PUT'])
@permission_classes([IsOwner])
def owner_profile(request):
    if request.method == 'GET':
        return Response(OwnerProfileSerializer(request.user).data)
    serializer = OwnerProfileSerializer(request.user, data=request.data, partial=True)
    serializer.is_valid(raise_exception=True)
    serializer.save()
    return Response(serializer.data)


@api_view(['POST'])
@permission_classes([IsOwner])
@parser_classes([MultiPartParser, FormParser])
def upload_business_license(request):
    """사업자등록증 업로드. 문서 확장자 화이트리스트 + 크기 제한 적용 (Secure 고정)."""
    upload = request.FILES.get('file')
    if not upload:
        return error_response('bad_request', 'file이 필요합니다.', 400)

    restaurant = Restaurant.objects.filter(owner=request.user).first()
    if not restaurant:
        restaurant = Restaurant.objects.create(owner=request.user, name=f'{request.user.username}의 매장')

    if True:  # 항상 Secure 검증 적용 (Vulnerable 분기 임시 제거)
        ext = os.path.splitext(upload.name)[1].lower()
        if ext not in _ALLOWED_DOC_EXT:
            return error_response('invalid_file_type', '허용되지 않는 파일 형식입니다.', 400)
        if upload.size > _MAX_DOC_BYTES:
            return error_response('file_too_large', '파일 크기는 10MB 이하만 허용됩니다.', 400)

    restaurant.business_license = upload
    restaurant.save(update_fields=['business_license'])
    return Response({'business_license': restaurant.business_license.url}, status=201)
