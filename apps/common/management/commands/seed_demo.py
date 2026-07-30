"""Seed demo data for the HackMin lab.

    python manage.py seed_demo

Creates users of each role, a couple of restaurants with menus, a coupon, and a
delivered order so every endpoint has something to work with. Idempotent-ish:
running twice creates duplicates, so use on a fresh DB.
"""

from django.core.management.base import BaseCommand

from accounts.models import User
from inquiries.models import Inquiry
from orders.models import Order, OrderItem
from promotions.models import Coupon, UserCoupon
from restaurants.models import Menu, MenuOption, MenuOptionGroup, Restaurant


class Command(BaseCommand):
    help = 'Seed demo users, restaurants, menus, coupon and a sample order.'

    def handle(self, *args, **options):
        def user(username, role, **extra):
            u = User.objects.filter(username=username).first()
            if not u:
                u = User.objects.create_user(username, 'pw1234', role=role, **extra)
                self.stdout.write(f'  + user {username} ({role})')
            return u

        admin = user('admin', User.Role.ADMIN, is_staff=True, is_superuser=True)
        alice = user('alice', User.Role.CUSTOMER, nickname='앨리스', phone='010-1234-5678')
        bob = user('bob', User.Role.CUSTOMER, nickname='밥')
        owner = user('owner', User.Role.OWNER, nickname='사장님')
        user('rider', User.Role.RIDER, nickname='라이더')

        rest = Restaurant.objects.filter(name='교촌치킨 강남점').first()
        if not rest:
            rest = Restaurant.objects.create(
                owner=owner, name='교촌치킨 강남점', cuisine_type='치킨',
                address='서울 강남구 테헤란로 152', latitude=37.5006, longitude=127.0366,
                min_order_amount=15000, delivery_fee=3000, rating=4.6, is_open=True,
            )
            menu = Menu.objects.create(restaurant=rest, name='허니콤보', price=20000, description='인기 메뉴')
            group = MenuOptionGroup.objects.create(menu=menu, name='맵기', is_required=True, max_select=1)
            MenuOption.objects.create(group=group, name='순한맛', extra_price=0)
            MenuOption.objects.create(group=group, name='매운맛', extra_price=500)
            Menu.objects.create(restaurant=rest, name='레드콤보', price=21000)
            self.stdout.write('  + restaurant 교촌치킨 강남점 (+menus)')

        Restaurant.objects.get_or_create(
            name='김밥천국 역삼점',
            defaults=dict(owner=owner, cuisine_type='분식', min_order_amount=8000,
                          delivery_fee=2000, rating=4.1, latitude=37.5008, longitude=127.0361),
        )

        coupon, created = Coupon.objects.get_or_create(
            code='WELCOME2000',
            defaults=dict(name='신규가입 2000원 할인', discount_type=Coupon.DiscountType.FIXED,
                          discount_value=2000, min_order_amount=10000, is_active=True),
        )
        Coupon.objects.get_or_create(
            code='HIDDEN50',
            defaults=dict(name='비활성 테스트 쿠폰', discount_value=5000, is_active=False),
        )
        UserCoupon.objects.get_or_create(user=alice, coupon=coupon)

        if not Order.objects.filter(user=alice).exists():
            order = Order.objects.create(
                user=alice, restaurant=rest, status=Order.Status.DELIVERED,
                subtotal=20000, delivery_fee=3000, discount=2000, total=21000,
                address='서울 강남구 테헤란로 152', request_note='문 앞에 놔주세요',
            )
            OrderItem.objects.create(order=order, menu=rest.menus.first(), menu_name='허니콤보',
                                     unit_price=20000, quantity=1, line_total=20000)
            self.stdout.write('  + sample delivered order for alice')

        # 1:1 문의 (Stored XSS 실습용) — 정상 문의 2건 + 은닉 악성 1건
        if not Inquiry.objects.exists():
            Inquiry.objects.create(
                user=alice, category=Inquiry.Category.REFUND, title='환불 문의',
                content='어제 주문한 치킨이 안 왔는데 환불 가능한가요? 확인 부탁드립니다.',
            )
            Inquiry.objects.create(
                user=bob, category=Inquiry.Category.DELIVERY, title='배달 지연',
                content='배달이 1시간 넘게 지연되고 있어요. 라이더 위치 좀 확인해주세요.',
            )
            # 🎯 은닉 저장형 XSS: 화면엔 평범한 문의로 보이지만, 관리자가 상세를
            #    열람하면 display:none 으로 숨긴 <img onerror> 가 조용히 실행된다.
            #    (작은따옴표·<script> 없이 작성해 허술한 필터를 통과한다.)
            Inquiry.objects.create(
                user=bob, category=Inquiry.Category.ETC, title='결제 오류 문의',
                content=(
                    '결제가 두 번 된 것 같아요. 확인 부탁드립니다.'
                    '<img src=x onerror=alert(document.cookie) style="display:none">'
                ),
            )
            self.stdout.write('  + 1:1 inquiries (정상 2 + 은닉 XSS 1)')

        self.stdout.write(self.style.SUCCESS(
            '\n완료. 모든 계정 비밀번호는 "pw1234" 입니다. '
            '(admin/alice/bob/owner/rider)'
        ))
