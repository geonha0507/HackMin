"""Customer "my page" endpoints (/api/v1/me)."""

from django.contrib.auth import get_user_model
from rest_framework import generics, status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response

from common.exceptions import error_response
from .crypto_utils import encrypt_aes256
from .models import Address
from .serializers import AddressSerializer, PasswordChangeSerializer, UserSerializer

User = get_user_model()


def _mask_tail(digits, visible=4):
    """뒤 `visible`자리만 남기고 나머지를 *로 가린 마스킹 문자열을 만든다."""
    if len(digits) <= visible:
        return digits
    return '*' * (len(digits) - visible) + digits[-visible:]


@api_view(['GET', 'POST'])
@permission_classes([IsAuthenticated])
def payment_cards(request):
    """등록 카드 목록(GET) / 카드 등록(POST). 카드번호는 AES-256 암호화 저장, 표시는 마스킹값."""
    from payments.models import PaymentCard

    if request.method == 'GET':
        # provider 쿼리로 필터 가능(card|kakao|naver). 없으면 전체.
        provider = request.query_params.get('provider')
        cards = PaymentCard.objects.filter(user=request.user)
        if provider:
            cards = cards.filter(provider=provider)
        return Response({'results': [
            {'id': c.id, 'provider': c.provider, 'card_masked': c.card_masked} for c in cards
        ]})

    # POST: 카드 등록 (provider: card|kakao|naver, 기본 card)
    provider = (request.data.get('provider') or 'card').strip()
    card_number = (request.data.get('card_number') or '').replace('-', '').replace(' ', '').strip()
    if not card_number.isdigit() or len(card_number) != 16:
        return error_response('bad_request', '카드번호가 올바르지 않습니다.', 400)
    masked = '****-****-****-' + card_number[-4:]
    card = PaymentCard.objects.create(
        user=request.user,
        provider=provider,
        card_number_encrypted=encrypt_aes256(card_number),
        card_masked=masked,
    )
    return Response(
        {'id': card.id, 'provider': card.provider, 'card_masked': card.card_masked},
        status=status.HTTP_201_CREATED,
    )


@api_view(['DELETE'])
@permission_classes([IsAuthenticated])
def payment_card_detail(request, pk):
    """등록 카드 삭제. 본인 카드만 삭제 가능."""
    from payments.models import PaymentCard

    card = PaymentCard.objects.filter(pk=pk, user=request.user).first()
    if not card:
        return error_response('not_found', '카드를 찾을 수 없습니다.', 404)
    card.delete()
    return Response(status=status.HTTP_204_NO_CONTENT)


@api_view(['GET', 'POST'])
@permission_classes([IsAuthenticated])
def payment_password(request):
    """결제 비밀번호 설정(POST) / 설정 여부 조회(GET). 6자리 숫자를 해시로 저장한다."""
    from django.contrib.auth.hashers import make_password

    if request.method == 'GET':
        return Response({'is_set': bool(request.user.payment_pw_hash)})

    # POST: 설정/변경
    pw = (request.data.get('password') or '').strip()
    if not pw.isdigit() or len(pw) != 6:
        return error_response('bad_request', '결제 비밀번호는 6자리 숫자여야 합니다.', 400)
    request.user.payment_pw_hash = make_password(pw)
    request.user.save(update_fields=['payment_pw_hash'])
    return Response({'is_set': True})


@api_view(['POST'])
@permission_classes([IsAuthenticated])
def payment_password_verify(request):
    """결제 비밀번호 검증. 결제 시 6자리를 서버 해시와 대조한다."""
    from django.contrib.auth.hashers import check_password

    pw = (request.data.get('password') or '').strip()
    if not request.user.payment_pw_hash:
        return error_response('not_set', '결제 비밀번호가 설정되지 않았습니다.', 400)
    valid = check_password(pw, request.user.payment_pw_hash)
    return Response({'valid': valid})


@api_view(['GET', 'POST'])
@permission_classes([IsAuthenticated])
def bank_accounts(request):
    """등록 계좌 목록(GET) / 계좌 등록(POST). 계좌번호는 AES-256 암호화 저장, 표시는 마스킹값."""
    from payments.models import BankAccount

    if request.method == 'GET':
        accounts = BankAccount.objects.filter(user=request.user)
        return Response({'results': [
            {'id': a.id, 'bank': a.bank, 'account_masked': a.account_masked} for a in accounts
        ]})

    # POST: 계좌 등록
    bank = (request.data.get('bank') or '').strip()
    account_number = (request.data.get('account_number') or '').replace('-', '').replace(' ', '').strip()
    if not bank:
        return error_response('bad_request', '은행을 선택해주세요.', 400)
    if not account_number.isdigit() or not (10 <= len(account_number) <= 14):
        return error_response('bad_request', '계좌번호가 올바르지 않습니다.', 400)
    account = BankAccount.objects.create(
        user=request.user,
        bank=bank,
        account_number_encrypted=encrypt_aes256(account_number),
        account_masked=_mask_tail(account_number, 4),
    )
    return Response(
        {'id': account.id, 'bank': account.bank, 'account_masked': account.account_masked},
        status=status.HTTP_201_CREATED,
    )


@api_view(['DELETE'])
@permission_classes([IsAuthenticated])
def bank_account_detail(request, pk):
    """등록 계좌 삭제. 본인 계좌만 삭제 가능."""
    from payments.models import BankAccount

    account = BankAccount.objects.filter(pk=pk, user=request.user).first()
    if not account:
        return error_response('not_found', '계좌를 찾을 수 없습니다.', 404)
    account.delete()
    return Response(status=status.HTTP_204_NO_CONTENT)


@api_view(['GET', 'PUT', 'DELETE'])
@permission_classes([IsAuthenticated])
def me(request):
    """내 정보 조회/수정/탈퇴."""
    if request.method == 'GET':
        return Response(UserSerializer(request.user).data)

    if request.method == 'PUT':
        serializer = UserSerializer(request.user, data=request.data, partial=True)
        serializer.is_valid(raise_exception=True)
        serializer.save()
        return Response(serializer.data)

    # DELETE -> 회원 탈퇴
    user = request.user
    user.status = User.Status.WITHDRAWN
    user.is_active = False
    user.save(update_fields=['status', 'is_active'])
    return Response(status=status.HTTP_204_NO_CONTENT)


@api_view(['PUT'])
@permission_classes([IsAuthenticated])
def change_password(request):
    serializer = PasswordChangeSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    user = request.user
    if not user.check_password(serializer.validated_data['old_password']):
        return error_response('invalid_password', '현재 비밀번호가 올바르지 않습니다.', 400)
    user.set_password(serializer.validated_data['new_password'])
    user.save(update_fields=['password'])
    return Response({'detail': '비밀번호가 변경되었습니다.'})


class AddressListCreateView(generics.ListCreateAPIView):
    serializer_class = AddressSerializer
    permission_classes = [IsAuthenticated]

    def get_queryset(self):
        return Address.objects.filter(user=self.request.user)

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)


class AddressDetailView(generics.RetrieveUpdateDestroyAPIView):
    """배송지 수정/삭제."""
    serializer_class = AddressSerializer
    permission_classes = [IsAuthenticated]

    def get_queryset(self):
        return Address.objects.filter(user=self.request.user)
