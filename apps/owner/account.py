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
from .serializers import (
    OwnerPasswordChangeSerializer,
    OwnerProfileSerializer,
    OwnerSignupSerializer,
    WithdrawalRequestSerializer,
)

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


@api_view(['GET', 'POST'])
@permission_classes([IsOwner])
def owner_withdrawal(request):
    """점주 탈퇴 요청 조회/접수.

    owner_profile 의 DELETE 는 계정을 즉시 비활성화한다. 반면 웹 화면의 탈퇴는
    관리자 승인을 거치는 요청 접수라 동작이 다르다. 그동안 이 흐름은
    apps/web 화면에만 있어 API 로는 불가능했다.
    """
    from accounts.models import WithdrawalRequest

    pending = WithdrawalRequest.objects.filter(
        user=request.user, status=WithdrawalRequest.Status.PENDING,
    ).first()

    if request.method == 'GET':
        return Response(
            {'pending': WithdrawalRequestSerializer(pending).data if pending else None}
        )

    # 비밀번호 재확인 없이 세션만으로 탈퇴가 접수되면 안 된다.
    password = request.data.get('password') or ''
    if not request.user.check_password(password):
        return error_response('invalid_password', '비밀번호가 올바르지 않습니다.', 400)
    if pending:
        return error_response('already_requested', '이미 처리 대기 중인 탈퇴 요청이 있습니다.', 409)

    created = WithdrawalRequest.objects.create(user=request.user)
    return Response(WithdrawalRequestSerializer(created).data, status=201)
