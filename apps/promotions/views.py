"""Coupons, favorites, membership endpoints (spec section 9 + /me)."""

from django.utils import timezone
from rest_framework import generics
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from common.exceptions import error_response
from common.permissions import IsCustomer
from restaurants.models import Menu
from .models import Coupon, Favorite, Membership, MembershipPayment, MembershipPointTransaction, UserCoupon
from .services import POINT_EARN_RATE
from .serializers import (
    CouponFullSerializer,
    CouponPublicSerializer,
    FavoriteSerializer,
    MembershipPaymentSerializer,
    MembershipPointTransactionSerializer,
    MembershipSerializer,
    UserCouponSerializer,
)

MEMBERSHIP_PRICE = 4900


def _has_active_membership(user):
    return Membership.objects.filter(user=user, status=Membership.Status.ACTIVE).exists()


# --- Coupons ---------------------------------------------------------------
@api_view(['GET'])
@permission_classes([IsCustomer])
def coupon_list(request):
    """다운로드 가능 쿠폰 목록. 활성 쿠폰만 노출하고 코드는 숨긴다."""
    coupons = Coupon.objects.filter(is_active=True)
    return Response({'results': CouponPublicSerializer(coupons, many=True).data})


@api_view(['POST'])
@permission_classes([IsCustomer])
def download_coupon(request, pk):
    """쿠폰 다운로드. 사용자당 1회로 제한하고, 멤버십 전용 쿠폰은 활성 멤버십 보유자만 받을 수 있다."""
    coupon = Coupon.objects.filter(pk=pk).first()
    if not coupon or not coupon.is_active:
        return error_response('not_found', '쿠폰을 찾을 수 없습니다.', 404)
    if coupon.is_membership_only and not _has_active_membership(request.user):
        return error_response('membership_required', '멤버십 전용 쿠폰입니다.', 403)

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
    if coupon.is_membership_only and not _has_active_membership(request.user):
        return error_response('membership_required', '멤버십 전용 쿠폰입니다.', 403)
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

# 멤버십 가입 시 지급하는 웰컴 쿠폰 5종. (code, name, 할인액, 최소주문액)
# UserCoupon(user, coupon) 유니크 제약 때문에 "5장"은 서로 다른 쿠폰으로 지급한다.
MEMBERSHIP_WELCOME_COUPONS = [
    ('MEMBERSHIP_WELCOME_1', '멤버십 가입 축하 쿠폰', 3000, 15000),
    ('MEMBERSHIP_WELCOME_2', '멤버십 가입 축하 쿠폰', 3000, 15000),
    ('MEMBERSHIP_WELCOME_3', '멤버십 가입 축하 쿠폰', 3000, 15000),
    ('MEMBERSHIP_WELCOME_4', '멤버십 가입 축하 쿠폰', 3000, 15000),
    ('MEMBERSHIP_WELCOME_5', '멤버십 가입 축하 쿠폰', 3000, 15000),
]


def _grant_membership_coupons(user):
    """멤버십 가입 시 웰컴 쿠폰 5장을 지급한다. 이미 보유한 쿠폰은 재지급하지 않는다."""
    for code, name, discount_value, min_order_amount in MEMBERSHIP_WELCOME_COUPONS:
        coupon, _ = Coupon.objects.get_or_create(
            code=code,
            defaults={
                'name': name,
                'discount_type': Coupon.DiscountType.FIXED,
                'discount_value': discount_value,
                'min_order_amount': min_order_amount,
                'is_active': True,
                'is_membership_only': True,
            },
        )
        # 사용 여부/사용시각은 UserCoupon이 관리 → 사용완료/중복적용 방지는 기존 로직을 그대로 탄다.
        UserCoupon.objects.get_or_create(user=user, coupon=coupon)


@api_view(['POST'])
@permission_classes([IsCustomer])
def membership_subscribe(request):
    """멤버십 가입. 모의 결제 기록을 남기고 basic 플랜으로 가입하며, 웰컴 쿠폰 5장을 지급한다."""
    membership, _ = Membership.objects.get_or_create(user=request.user)

    membership.plan = Membership.Plan.BASIC
    membership.status = Membership.Status.ACTIVE
    membership.cancelled_at = None
    membership.save()
    MembershipPayment.objects.create(membership=membership, amount=MEMBERSHIP_PRICE)
    _grant_membership_coupons(request.user)
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
            {'title': '추가 적립', 'description': f'주문 금액의 {POINT_EARN_RATE}% 포인트 적립'},
            {'title': '전용 할인', 'description': '멤버십 전용가 상품 이용'},
        ],
        'price': MEMBERSHIP_PRICE,
        'membership_only_coupon_count': Coupon.objects.filter(
            is_active=True, is_membership_only=True,
        ).count(),
        'membership_only_product_count': Menu.objects.filter(
            is_membership_only=True, status=Menu.Status.ON_SALE,
        ).count(),
    })


@api_view(['GET'])
@permission_classes([IsCustomer])
def membership_points(request):
    """포인트 적립/사용 내역 (/me/membership/points)."""
    membership = Membership.objects.filter(user=request.user).first()
    if not membership:
        return Response({'balance': 0, 'results': []})
    transactions = membership.point_transactions.all()
    return Response({
        'balance': membership.points,
        'results': MembershipPointTransactionSerializer(transactions, many=True).data,
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
