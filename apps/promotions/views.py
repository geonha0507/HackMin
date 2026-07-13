"""Coupons, favorites, membership endpoints (spec section 9 + /me)."""

from django.utils import timezone
from rest_framework import generics
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from common.exceptions import error_response
from common.mode import is_vulnerable
from common.permissions import IsCustomer
from .models import Coupon, Favorite, Membership, MembershipPayment, UserCoupon
from .serializers import (
    CouponFullSerializer,
    CouponPublicSerializer,
    FavoriteSerializer,
    MembershipPaymentSerializer,
    MembershipSerializer,
    UserCouponSerializer,
)

MEMBERSHIP_PRICE = 4900


# --- Coupons ---------------------------------------------------------------
@api_view(['GET'])
@permission_classes([IsCustomer])
def coupon_list(request):
    """🎯 다운로드 가능 쿠폰 목록.

    Vulnerable 모드: 비활성 쿠폰까지 포함하고 쿠폰 코드를 노출한다.
    Secure 모드: 활성 쿠폰만, 코드는 숨긴다.
    """
    if is_vulnerable(request):
        coupons = Coupon.objects.all()
        return Response({'results': CouponFullSerializer(coupons, many=True).data})
    coupons = Coupon.objects.filter(is_active=True)
    return Response({'results': CouponPublicSerializer(coupons, many=True).data})


@api_view(['POST'])
@permission_classes([IsCustomer])
def download_coupon(request, pk):
    """🎯 쿠폰 다운로드.

    Vulnerable 모드: 중복 다운로드 제한 없음(수량 무제한 발급).
    Secure 모드: 사용자당 1회로 제한.
    """
    coupon = Coupon.objects.filter(pk=pk).first()
    if not coupon or (not coupon.is_active and not is_vulnerable(request)):
        return error_response('not_found', '쿠폰을 찾을 수 없습니다.', 404)

    if is_vulnerable(request):
        uc = UserCoupon.objects.create(user=request.user, coupon=coupon)
        return Response(UserCouponSerializer(uc).data, status=201)

    uc, created = UserCoupon.objects.get_or_create(user=request.user, coupon=coupon)
    if not created:
        return error_response('already_downloaded', '이미 다운로드한 쿠폰입니다.', 409)
    return Response(UserCouponSerializer(uc).data, status=201)


@api_view(['POST'])
@permission_classes([IsCustomer])
def register_coupon(request):
    """쿠폰 코드 등록."""
    code = request.data.get('code')
    if not code:
        return error_response('bad_request', '쿠폰 코드가 필요합니다.', 400)
    coupon = Coupon.objects.filter(code=code, is_active=True).first()
    if not coupon:
        return error_response('coupon_not_found', '유효하지 않은 쿠폰 코드입니다.', 404)
    uc, created = UserCoupon.objects.get_or_create(user=request.user, coupon=coupon)
    if not created:
        return error_response('already_registered', '이미 등록된 쿠폰입니다.', 409)
    return Response(UserCouponSerializer(uc).data, status=201)


class MyCouponListView(generics.ListAPIView):
    """보유 쿠폰 조회 (/me/coupons)."""
    serializer_class = UserCouponSerializer
    permission_classes = [IsCustomer]

    def get_queryset(self):
        return (
            UserCoupon.objects.filter(user=self.request.user)
            .select_related('coupon').order_by('-downloaded_at')
        )


# --- Favorites -------------------------------------------------------------
class FavoriteListCreateView(generics.ListCreateAPIView):
    serializer_class = FavoriteSerializer
    permission_classes = [IsCustomer]

    def get_queryset(self):
        return (
            Favorite.objects.filter(user=self.request.user)
            .select_related('restaurant').order_by('-created_at')
        )

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)


@api_view(['DELETE'])
@permission_classes([IsCustomer])
def delete_favorite(request, pk):
    fav = Favorite.objects.filter(pk=pk, user=request.user).first()
    if not fav:
        return error_response('not_found', '찜을 찾을 수 없습니다.', 404)
    fav.delete()
    return Response(status=204)


# --- Membership ------------------------------------------------------------
@api_view(['POST'])
@permission_classes([IsCustomer])
def membership_subscribe(request):
    """🎯 멤버십 가입.

    Vulnerable 모드: 결제 없이 요청 body의 plan을 그대로 신뢰하여 즉시 활성화.
    Secure 모드: 모의 결제 기록을 남기고 basic 플랜으로 가입.
    """
    membership, _ = Membership.objects.get_or_create(user=request.user)

    if is_vulnerable(request):
        membership.plan = request.data.get('plan', Membership.Plan.PREMIUM)
        membership.status = Membership.Status.ACTIVE
        membership.cancelled_at = None
        membership.save()
        return Response(MembershipSerializer(membership).data, status=201)

    membership.plan = Membership.Plan.BASIC
    membership.status = Membership.Status.ACTIVE
    membership.cancelled_at = None
    membership.save()
    MembershipPayment.objects.create(membership=membership, amount=MEMBERSHIP_PRICE)
    return Response(MembershipSerializer(membership).data, status=201)


@api_view(['POST'])
@permission_classes([IsCustomer])
def membership_cancel(request):
    membership = Membership.objects.filter(user=request.user).first()
    if not membership:
        return error_response('not_found', '멤버십이 없습니다.', 404)
    membership.status = Membership.Status.CANCELLED
    membership.cancelled_at = timezone.now()
    membership.save(update_fields=['status', 'cancelled_at'])
    return Response(MembershipSerializer(membership).data)


@api_view(['GET'])
@permission_classes([IsCustomer])
def membership_benefits(_request):
    return Response({
        'benefits': [
            {'title': '무료배달', 'description': '멤버십 전용 무료배달 쿠폰 월 5장'},
            {'title': '추가 적립', 'description': '주문 금액의 3% 포인트 적립'},
            {'title': '전용 할인', 'description': '멤버십 전용가 상품 이용'},
        ],
        'price': MEMBERSHIP_PRICE,
    })


@api_view(['GET'])
@permission_classes([IsCustomer])
def membership_payments(request):
    membership = Membership.objects.filter(user=request.user).first()
    if not membership:
        return Response({'results': []})
    payments = membership.payments.all()
    return Response({'results': MembershipPaymentSerializer(payments, many=True).data})


@api_view(['GET'])
@permission_classes([IsCustomer])
def my_membership(request):
    """멤버십 상태 조회 (/me/membership)."""
    membership = Membership.objects.filter(user=request.user).first()
    if not membership:
        return Response({'plan': None, 'status': 'none'})
    return Response(MembershipSerializer(membership).data)
