"""Customer "my page" endpoints (/api/v1/me)."""

from django.contrib.auth import get_user_model
from rest_framework import generics, status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response

from common.exceptions import error_response
from common.mode import is_vulnerable
from .models import Address
from .serializers import AddressSerializer, PasswordChangeSerializer, UserSerializer

User = get_user_model()


@api_view(['GET', 'PUT', 'DELETE'])
@permission_classes([IsAuthenticated])
def me(request):
    """🎯 내 정보 조회/수정/탈퇴.

    Vulnerable 모드는 ?user_id= 파라미터로 임의 사용자 조회를 허용(IDOR).
    """
    if request.method == 'GET':
        target = request.user
        if is_vulnerable(request):
            spoof_id = request.query_params.get('user_id')
            if spoof_id:
                target = User.objects.filter(pk=spoof_id).first() or request.user
        return Response(UserSerializer(target).data)

    if request.method == 'PUT':
        # 🎯 Vulnerable 모드는 role/status 같은 보호 필드까지 대량 할당 허용(Mass Assignment).
        if is_vulnerable(request):
            user = request.user
            for field in ('email', 'phone', 'nickname', 'role', 'status', 'is_staff'):
                if field in request.data:
                    setattr(user, field, request.data[field])
            user.save()
            return Response(UserSerializer(user).data)
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
    """🎯 배송지 수정/삭제.

    Vulnerable 모드는 소유자 검증 없이 pk로만 조회(IDOR).
    """
    serializer_class = AddressSerializer
    permission_classes = [IsAuthenticated]

    def get_queryset(self):
        if is_vulnerable(self.request):
            return Address.objects.all()
        return Address.objects.filter(user=self.request.user)
