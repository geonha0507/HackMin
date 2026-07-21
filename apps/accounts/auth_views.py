"""Authentication endpoints (/api/v1/auth).

Email / phone / OTP verification is intentionally out of scope for now, so the
corresponding spec endpoints are not implemented here.

🎯 endpoints implement both Vulnerable and Secure behaviour, selected per
request via common.mode (settings.HACKMIN_MODE or the X-Hackmin-Mode header).
"""

from django.contrib.auth import authenticate, get_user_model
from django.db import connection
from rest_framework import status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework_simplejwt.exceptions import TokenError
from rest_framework_simplejwt.tokens import RefreshToken

from common.exceptions import error_response
from common.mode import is_vulnerable
from .serializers import LoginSerializer, SignupSerializer, UserSerializer

User = get_user_model()


def _issue_tokens(user):
    refresh = RefreshToken.for_user(user)
    return {
        'access': str(refresh.access_token),
        'refresh': str(refresh),
    }


@api_view(['POST'])
@permission_classes([AllowAny])
def signup(request):
    serializer = SignupSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    user = serializer.save()
    tokens = _issue_tokens(user)
    return Response(
        {'user': UserSerializer(user).data, **tokens},
        status=status.HTTP_201_CREATED,
    )


@api_view(['POST'])
@permission_classes([AllowAny])
def login(request):
    """🎯 로그인. Vulnerable 모드는 SQL Injection에 취약하다."""
    serializer = LoginSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    username = serializer.validated_data['username']
    password = serializer.validated_data['password']

    if is_vulnerable(request):
        # VULNERABLE: 사용자 입력을 그대로 문자열 연결 -> SQL Injection.
        # 예) username = "admin' -- " 로 인증을 우회할 수 있다.
        query = f"SELECT id FROM accounts_user WHERE username = '{username}'"  # noqa
        with connection.cursor() as cursor:
            cursor.execute(query)
            row = cursor.fetchone()
        if not row:
            return error_response('invalid_credentials', '아이디 또는 비밀번호가 올바르지 않습니다.', 401)
        user = User.objects.get(pk=row[0])
        # 취약 모드에서는 비밀번호 검증을 생략(인젝션 시연 목적).
        tokens = _issue_tokens(user)
        return Response({'user': UserSerializer(user).data, **tokens})

    # SECURE: 파라미터 바인딩 + 해시 기반 인증.
    user = authenticate(request, username=username, password=password)
    if user is None:
        return error_response('invalid_credentials', '아이디 또는 비밀번호가 올바르지 않습니다.', 401)
    if not user.is_active or user.status != User.Status.ACTIVE:
        return error_response('account_inactive', '비활성화된 계정입니다.', 403)
    tokens = _issue_tokens(user)
    return Response({'user': UserSerializer(user).data, **tokens})


@api_view(['POST'])
@permission_classes([IsAuthenticated])
def logout(request):
    """로그아웃. 전달된 refresh 토큰을 블랙리스트 처리(가능한 경우)한다."""
    refresh = request.data.get('refresh')
    if refresh:
        try:
            token = RefreshToken(refresh)
            token.blacklist()
        except (TokenError, AttributeError):
            # 블랙리스트 앱이 미설치면 조용히 무시(무상태 로그아웃).
            pass
    return Response({'detail': '로그아웃되었습니다.'})


@api_view(['POST'])
@permission_classes([AllowAny])
def refresh_token(request):
    """🎯 액세스 토큰 갱신."""
    token_str = request.data.get('refresh')
    if not token_str:
        return error_response('bad_request', 'refresh 토큰이 필요합니다.', 400)

    if is_vulnerable(request):
        # VULNERABLE: 서명/만료 검증 없이 payload의 user_id를 신뢰하여
        # 새 액세스 토큰을 발급한다 -> 임의 사용자 사칭 가능.
        import jwt as _jwt
        try:
            payload = _jwt.decode(token_str, options={'verify_signature': False})
        except Exception:
            return error_response('invalid_token', '토큰을 해석할 수 없습니다.', 401)
        user_id = payload.get('user_id')
        try:
            user = User.objects.get(pk=user_id)
        except User.DoesNotExist:
            return error_response('invalid_token', '유효하지 않은 토큰입니다.', 401)
        return Response({'access': str(RefreshToken.for_user(user).access_token)})

    # SECURE: 서명/만료 검증 후 갱신.
    try:
        refresh = RefreshToken(token_str)
    except TokenError:
        return error_response('invalid_token', '유효하지 않거나 만료된 토큰입니다.', 401)
    return Response({'access': str(refresh.access_token)})


@api_view(['GET'])
@permission_classes([AllowAny])
def check_duplicate(request):
    """이메일/아이디/닉네임 중복 검사. ?username= 또는 ?email= 또는 ?nickname= 지원."""
    username = request.query_params.get('username')
    email = request.query_params.get('email')
    nickname = request.query_params.get('nickname')
    if not username and not email and not nickname:
        return error_response('bad_request', 'username, email 또는 nickname 파라미터가 필요합니다.', 400)
    exists = False
    if username:
        exists = User.objects.filter(username=username).exists()
    elif email:
        exists = User.objects.filter(email=email).exists()
    elif nickname:
        exists = User.objects.filter(nickname=nickname).exists()
    return Response({'available': not exists, 'exists': exists})


@api_view(['POST'])
@permission_classes([AllowAny])
def password_reset_request(request):
    """🎯 비밀번호 재설정 요청.

    이메일/휴대폰 인증은 범위 밖이므로, 데모 편의상 재설정 토큰을 즉시 반환한다.
    Vulnerable 모드는 사용자 존재 여부를 응답으로 노출(계정 열거 취약).
    """
    username = request.data.get('username') or request.data.get('email')
    if not username:
        return error_response('bad_request', 'username 또는 email이 필요합니다.', 400)

    user = User.objects.filter(username=username).first() or \
        User.objects.filter(email=username).first()

    if is_vulnerable(request):
        # VULNERABLE: 계정 존재 여부와 재설정 토큰을 그대로 노출.
        if not user:
            return error_response('user_not_found', '존재하지 않는 사용자입니다.', 404)
        reset_token = str(RefreshToken.for_user(user).access_token)
        return Response({'reset_token': reset_token, 'user_id': user.id})

    # SECURE: 계정 존재 여부를 노출하지 않는 일관된 응답.
    return Response({'detail': '재설정 안내가 등록된 연락처로 전송되었습니다(존재하는 경우).'})


@api_view(['POST'])
@permission_classes([AllowAny])
def password_reset(request):
    """비밀번호 재설정 실행. reset_token + new_password."""
    token_str = request.data.get('reset_token')
    new_password = request.data.get('new_password')
    if not token_str or not new_password:
        return error_response('bad_request', 'reset_token과 new_password가 필요합니다.', 400)
    # reset_token은 access 토큰 형태이므로 서명 검증 후 user_id 사용.
    from rest_framework_simplejwt.tokens import AccessToken
    try:
        access = AccessToken(token_str)
        user = User.objects.get(pk=access['user_id'])
    except Exception:
        return error_response('invalid_token', '유효하지 않거나 만료된 토큰입니다.', 401)
    user.set_password(new_password)
    user.save(update_fields=['password'])
    return Response({'detail': '비밀번호가 변경되었습니다.'})
