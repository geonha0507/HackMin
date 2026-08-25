"""한국 사업자등록번호(BRN) 유틸 — 형식/체크섬 검증 및 테스트용 더미 생성.

프로덕션 용도: 가입/매장등록 폼의 입력값 형식·체크섬 검증(`is_valid_brn`).
테스트 용도: 국세청에 실재하지 않지만 체크섬은 통과하는 더미 번호 생성
            (`fake_brn`) — 시드/픽스처 전용. 실존 사업자와 무관하다.

체크섬 규칙(공개 알고리즘):
    가중치 [1,3,7,1,3,7,1,3,5]를 앞 9자리에 곱해 합산하고,
    9번째 자리(index 8) * 5 의 몫(//10)을 더한 뒤,
    (10 - 합 % 10) % 10 이 마지막 자리와 같아야 유효.
"""

from __future__ import annotations

import random
import re

_WEIGHTS = (1, 3, 7, 1, 3, 7, 1, 3, 5)
_DIGITS_RE = re.compile(r"\D")


def _check_digit(first9: list[int]) -> int:
    total = sum(d * w for d, w in zip(first9, _WEIGHTS))
    total += (first9[8] * 5) // 10
    return (10 - (total % 10)) % 10


def is_valid_brn(value: str) -> bool:
    """'123-45-67890' 또는 '1234567890' 형식을 받아 체크섬까지 검증."""
    digits = _DIGITS_RE.sub("", value or "")
    if len(digits) != 10 or not digits.isdigit():
        return False
    nums = [int(c) for c in digits]
    return _check_digit(nums[:9]) == nums[9]


def format_brn(digits: str) -> str:
    """'1234567890' -> '123-45-67890'."""
    d = _DIGITS_RE.sub("", digits)
    if len(d) != 10:
        raise ValueError("사업자번호는 숫자 10자리여야 합니다.")
    return f"{d[:3]}-{d[3:5]}-{d[5:]}"


def fake_brn(rng: random.Random | None = None, dashed: bool = True) -> str:
    """체크섬 통과하는 더미 번호 생성(테스트 전용).

    앞 3자리는 세무서 코드 자리이나, 실존 등록과의 우연한 일치를 피하려고
    민간 사업자에 쓰이지 않는 000 대역을 쓴다. 국세청 조회에는 잡히지 않는다.
    """
    r = rng or random.Random()
    head = [0, 0, 0]                       # 실등록 회피용 예약 대역
    mid = [r.randint(0, 9) for _ in range(6)]
    first9 = head + mid
    last = _check_digit(first9)
    digits = "".join(str(d) for d in first9 + [last])
    return format_brn(digits) if dashed else digits


if __name__ == "__main__":
    seeded = random.Random(20260822)
    print("== 더미 사업자번호(체크섬 유효, 실존 X) ==")
    samples = [fake_brn(seeded) for _ in range(8)]
    for s in samples:
        print(f"  {s}   valid={is_valid_brn(s)}")
    print("\n== 검증 함수 스모크 테스트 ==")
    for v, exp in [("000-11-22334", None), ("123-45-67890", False), ("abc", False)]:
        print(f"  {v!r:16} -> is_valid_brn={is_valid_brn(v)}")
