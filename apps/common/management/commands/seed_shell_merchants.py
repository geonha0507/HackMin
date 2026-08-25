"""위장 가맹점(대포사업자) 시드 — 자가진단/이상거래 탐지 시나리오 전용.

    python manage.py seed_shell_merchants [--count N]

체크섬만 통과하는 더미 사업자번호(apps.common.brn.fake_brn)를 부여한
'단기 개업 + 정산계좌 잦은 변경' 패턴의 가맹점을 심는다. 모두 가공 데이터로
국세청/실존 사업자와 무관하며, 탐지 룰·정산 게이트를 테스트하기 위한 것.
"""

from random import Random

from django.core.management.base import BaseCommand

from accounts.models import User
from common.brn import fake_brn
from restaurants.models import Restaurant

_SHELL_NAMES = [
    "24시 즉석대박분식", "번개상회 임시매장", "가성비폭탄치킨",
    "새벽특가마트", "한입족발 팝업점", "무한리필피자 임시",
]


class Command(BaseCommand):
    help = "위장 가맹점(더미 BRN) 시드 — 이상거래 탐지 시나리오용."

    def add_arguments(self, parser):
        parser.add_argument("--count", type=int, default=3)

    def handle(self, *args, **options):
        rng = Random(20260822)
        n = max(1, min(options["count"], len(_SHELL_NAMES)))

        owner = User.objects.filter(username="shellowner").first()
        if not owner:
            owner = User.objects.create_user(
                "shellowner", "pw1234", role=User.Role.OWNER, nickname="위장사장",
            )
            self.stdout.write("  + owner shellowner (OWNER)")

        for i in range(n):
            name = _SHELL_NAMES[i]
            brn = fake_brn(rng)
            rest, created = Restaurant.objects.get_or_create(
                name=name,
                defaults=dict(
                    owner=owner, cuisine_type="분식", is_open=True,
                    min_order_amount=5000,
                    delivery_fee=6900,          # 비정상적으로 높은 배달비(정산 표적)
                    rating=4.9,                 # 신규인데 만점대(리뷰 조작 흔적)
                    address="서울 어딘가 123",
                    description=f"[SEED-SHELL] BRN={brn} 단기개업/계좌변경다발",
                ),
            )
            tag = "+" if created else "=" 
            self.stdout.write(f"  {tag} shell-merchant {name}  BRN={brn}")

        self.stdout.write(self.style.SUCCESS(
            f"위장 가맹점 {n}건 시드 완료(더미 BRN, 실존 X)."))
