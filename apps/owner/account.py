"""Owner account endpoints (/api/v1/owner/{signup,profile,business-license})."""

import os
import re

from django.contrib.auth import get_user_model
from django.db import IntegrityError, transaction
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


# --------------------------------------------------------------- 입점 신청 가입
User = get_user_model()


def _is_valid_account_number(account_number):
    """계좌번호 형식 검증. 하이픈을 제외한 8~20자리 숫자만 허용한다."""
    digits = (account_number or '').replace('-', '')
    return digits.isdigit() and 8 <= len(digits) <= 20


@api_view(['POST'])
@permission_classes([AllowAny])
@parser_classes([MultiPartParser, FormParser])
def store_signup(request):
    """점주 계정 + 매장 입점 신청을 함께 생성한다.

    기존 owner_signup 은 계정만 만드는 단순 가입이다. 이쪽은 웹 회원가입
    화면의 흐름으로, 계좌번호와 사업자등록증을 받고 계정을 PENDING
    상태로 만들어 관리자 승인 전에는 로그인할 수 없게 한다.
    이 로직은 그동안 apps/web 화면에만 있었다.
    """
    data = request.data
    username = (data.get('username') or '').strip()
    email = (data.get('email') or '').strip()
    phone = (data.get('phone') or '').strip()
    nickname = (data.get('nickname') or '').strip()
    account_number = (data.get('account_number') or '').strip()
    password = data.get('password') or ''

    store_name = (data.get('store_name') or '').strip()
    store_phone = (data.get('store_phone') or '').strip()
    store_address = (data.get('store_address') or '').strip()

    license_file = request.FILES.get('business_license')

    # 화면 순서와 같은 순서로 검사해 사용자가 받는 메시지를 유지한다.
    checks = [
        (not username, '아이디를 입력하세요.'),
        (not email, '이메일을 입력하세요.'),
        (not nickname, '점주명을 입력하세요.'),
        (not account_number, '계좌번호를 입력하세요.'),
        (account_number and not _is_valid_account_number(account_number), '계좌번호 형식이 올바르지 않습니다.'),
        (not store_name, '매장명을 입력하세요.'),
        (not store_address, '매장 주소를 입력하세요.'),
        (not license_file, '사업자등록증 파일을 첨부하세요.'),
        (not password, '비밀번호를 입력하세요.'),
        (bool(password) and len(password) < 8, '비밀번호는 8자 이상 입력하세요.'),
    ]
    for failed, message in checks:
        if failed:
            return error_response('bad_request', message, 400)

    if User.objects.filter(username=username).exists():
        return error_response('duplicate_username', '이미 사용 중인 아이디입니다.', 409)
    if User.objects.filter(email__iexact=email).exists():
        return error_response('duplicate_email', '이미 사용 중인 이메일입니다.', 409)

    ext = os.path.splitext(license_file.name)[1].lower()
    if ext not in _ALLOWED_DOC_EXT:
        return error_response(
            'invalid_file_type', '사업자등록증은 PDF, JPG, JPEG, PNG 파일만 가능합니다.', 400,
        )
    if license_file.size > _MAX_DOC_BYTES:
        return error_response('file_too_large', '사업자등록증 파일은 10MB 이하만 가능합니다.', 400)

    from accounts.crypto_utils import encrypt_aes128

    try:
        # User 와 Restaurant 중 하나만 저장되는 일을 막는다.
        with transaction.atomic():
            owner = User.objects.create_user(
                username=username,
                password=password,
                email=email,
                phone=phone,
                nickname=nickname,
                role=User.Role.OWNER,
                # 관리자 승인 전 상태. 로그인은 auth/login 이 status 로 막는다.
                status=User.Status.PENDING,
                is_active=True,
                account_number_encrypted=encrypt_aes128(account_number),
            )
            Restaurant.objects.create(
                owner=owner,
                name=store_name,
                phone=store_phone or phone,
                address=store_address,
                business_license=license_file,
                # 승인 전에는 영업 중으로 표시하지 않는다.
                is_open=False,
            )
    except IntegrityError:
        return error_response('duplicate', '이미 사용 중인 계정 정보입니다.', 409)

    return Response(
        {'detail': '입점 신청이 완료되었습니다. 관리자 승인 후 로그인할 수 있습니다.'},
        status=status.HTTP_201_CREATED,
    )
