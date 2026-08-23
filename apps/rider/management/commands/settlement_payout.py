"""정산 배치: 지급 확정(서명·거리 도장 검증)된 배달료를 라이더 정산계좌로 지급한다.

    python manage.py settlement_payout

각 라이더의 '현재 저장된 정산계좌'로 지급하므로, IDOR(rider_account_by_id)로 계좌가
공격자 것으로 바뀐 라이더는 **바뀐(공격자) 계좌로 지급**된다(계좌 스왑 → 배치 지급 → 절도).
지급 후 계좌별 집계를 출력한다 — 여러 라이더의 지급이 동일 계좌로 몰리면 이상신호(탐지 지점).
"""
from collections import defaultdict

from django.core.management.base import BaseCommand

from rider.views import run_payout_batch


class Command(BaseCommand):
    help = '정산 배치: 지급 확정 배달료를 라이더 정산계좌로 지급.'

    def handle(self, *args, **options):
        results = run_payout_batch()
        if not results:
            self.stdout.write('지급 대상 없음(서명·도장 검증 통과한 미지급 배달이 없습니다).')
            return

        total = 0
        by_account = defaultdict(lambda: [0, 0])  # "은행 계좌" -> [금액합, 건수]
        self.stdout.write('=== 지급 내역 ===')
        for r in results:
            self.stdout.write(
                f"  라이더 {r['rider_id']:>4}: {r['amount']:>12,}원 → "
                f"{r['bank']} {r['account']} ({r['holder']})")
            total += r['amount']
            key = f"{r['bank']} {r['account']} ({r['holder']})"
            by_account[key][0] += r['amount']
            by_account[key][1] += 1

        self.stdout.write('\n=== 계좌별 집계 ===')
        for acc, (amt, cnt) in sorted(by_account.items(), key=lambda x: -x[1][0]):
            flag = '   ⚠️ 다수 라이더 → 동일 계좌(이상신호)' if cnt >= 3 else ''
            self.stdout.write(f"  {acc:<32} {amt:>14,}원  ({cnt}건){flag}")

        self.stdout.write(self.style.SUCCESS(f"\n총 지급액: {total:,}원 ({len(results)}건)"))
