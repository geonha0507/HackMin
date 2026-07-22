"""Membership point + signup coupon helpers."""

from .models import Coupon, Membership, MembershipPointTransaction, UserCoupon

POINT_EARN_RATE = 3  # percent — membership_benefits() 안내 문구와 동일하게 맞춘다.


# 회원가입 시 지급하는 신규가입 쿠폰 4종.
# (code, name, discount_type, discount_value, min_order_amount)
SIGNUP_WELCOME_COUPONS = [
    ('SIGNUP_FREE_DELIVERY', '배달비 무료 쿠폰', Coupon.DiscountType.FIXED, 3000, 12000),
    ('SIGNUP_5000_OVER_30000', '3만원 이상 5,000원 할인', Coupon.DiscountType.FIXED, 5000, 30000),
    ('SIGNUP_3000', '신규가입 축하 3,000원 할인', Coupon.DiscountType.FIXED, 3000, 10000),
    ('SIGNUP_10PERCENT', '10% 할인 쿠폰', Coupon.DiscountType.PERCENT, 10, 15000),
]


def grant_signup_coupons(user):
    """회원가입 시 신규가입 쿠폰 4종을 지급한다. 이미 보유한 쿠폰은 재지급하지 않는다.

    쿠폰은 멤버십과 무관하게 사용 가능하며(is_membership_only=False), 사용완료/중복적용
    방지는 결제·apply_coupon의 기존 로직을 그대로 탄다.
    """
    for code, name, discount_type, discount_value, min_order_amount in SIGNUP_WELCOME_COUPONS:
        coupon, _ = Coupon.objects.get_or_create(
            code=code,
            defaults={
                'name': name,
                'discount_type': discount_type,
                'discount_value': discount_value,
                'min_order_amount': min_order_amount,
                'is_active': True,
                'is_membership_only': False,
            },
        )
        UserCoupon.objects.get_or_create(user=user, coupon=coupon)


def award_order_points(order):
    """배달 완료된 주문에 대해 활성 멤버십 회원에게 포인트를 적립한다.

    주문 취소/거절 건은 호출되지 않으므로(스펙 5.2 "주문 취소 후 포인트 유지")
    별도의 취소 시 차감 로직은 두지 않는다.
    """
    membership = Membership.objects.filter(
        user=order.user, status=Membership.Status.ACTIVE,
    ).first()
    if not membership:
        return None
    if MembershipPointTransaction.objects.filter(order=order, type=MembershipPointTransaction.Type.EARN).exists():
        return None  # 중복 적립 방지 (동일 주문에 대해 상태 변경이 여러 번 호출되어도 1회만 적립)

    earned = order.total * POINT_EARN_RATE // 100
    if earned <= 0:
        return None

    membership.points += earned
    membership.save(update_fields=['points'])
    return MembershipPointTransaction.objects.create(
        membership=membership,
        order=order,
        type=MembershipPointTransaction.Type.EARN,
        amount=earned,
        balance_after=membership.points,
    )
