"""Membership point helpers."""

from .models import Membership, MembershipPointTransaction

POINT_EARN_RATE = 3  # percent — membership_benefits() 안내 문구와 동일하게 맞춘다.


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
