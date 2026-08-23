from django.conf import settings
from django.db import models


class Delivery(models.Model):
    class Status(models.TextChoices):
        ASSIGNED = 'assigned', 'Assigned'
        PICKED_UP = 'picked_up', 'Picked up'
        DELIVERING = 'delivering', 'Delivering'
        DELIVERED = 'delivered', 'Delivered'

    order = models.OneToOneField('orders.Order', on_delete=models.CASCADE, related_name='delivery')
    rider = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True,
        related_name='deliveries',
    )
    status = models.CharField(max_length=16, choices=Status.choices, default=Status.ASSIGNED)
    assigned_at = models.DateTimeField(auto_now_add=True)
    completed_at = models.DateTimeField(null=True, blank=True)
    # 배달 완료 시 앱(클라이언트)이 GPS로 계산해 보고한 이동 거리(km)와 그로 산정된 배달료.
    # 서버는 보고된 거리를 그대로 신뢰해 요금을 계산한다(거리 기반 정산).
    distance_km = models.FloatField(default=0)
    fee = models.PositiveIntegerField(default=0)
    # 배달 시작(픽업/배달중) 시점의 라이더 누적 이동거리 스냅샷. 배달완료 시
    # (현재 누적거리 - 이 스냅샷) 이 '이 배달에서 서버가 관측한 실제 이동거리'다.
    # 서버는 이 값으로 배달료를 산정한다(클라가 보고한 distance_km 는 신뢰하지 않는다).
    start_distance_km = models.FloatField(default=0)
    # 정산 확정 여부. 라이더가 '배달완료'로 바꿔도 배달료는 '정산 대기'일 뿐이고,
    # 주문한 고객이 수령확인(POST /orders/<id>/confirm-receipt)을 해야 settled=True 가
    # 되어 지급 대상이 된다. 고객 확인 없이는 라이더가 돈을 받을 수 없다.
    settled = models.BooleanField(default=False)
    settled_at = models.DateTimeField(null=True, blank=True)
    # [정산 fix⑤] 서버가 관측·확정한 이동거리에 대한 무결성 도장(env 키 HMAC).
    # 배달완료 시 (delivery_id, distance_km)로 계산해 저장한다. 정산 집계는 이 도장이
    # 맞는 거리만 인정하므로, SQLi 로 distance_km 를 바꿔 써도 도장 불일치로 무효가 된다.
    distance_seal = models.CharField(max_length=64, blank=True, default='')
    # [정산 fix③] 고객 수령확인 서명 증거(정산 재검증용). {ts, nonce, sig, key_id, path}.
    # 정산 집계는 이 서명을 '등록 공개키'로 다시 검증한 배달만 지급 대상으로 삼는다.
    # SQLi 로 임의 값을 써 넣어도 TEE 개인키가 만든 유효 서명이 아니면 검증에서 탈락한다.
    receipt_proof = models.JSONField(null=True, blank=True)
    # 정산 배치가 이 배달의 배달료를 라이더 계좌로 지급했는지(중복 지급 방지).
    paid_out = models.BooleanField(default=False)

    def __str__(self):
        return f'Delivery(order={self.order_id}, {self.status})'


class RiderProfile(models.Model):
    """배달 시작 전 라이더가 입력하는 정산·자격 정보(해킹커넥트 온보딩).

    계좌번호는 AES-128 암호화 컬럼에 저장한다(accounts.crypto_utils). 나머지
    (면허번호·차량번호 등)는 평문. 실서비스라면 면허·차량도 마스킹/암호화 검토 필요.
    """
    class DeliveryMethod(models.TextChoices):
        WALK = 'walk', '도보'
        BICYCLE = 'bicycle', '자전거'
        MOTORCYCLE = 'motorcycle', '오토바이'
        CAR = 'car', '자동차'

    rider = models.OneToOneField(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='rider_profile',
    )
    # 정산 계좌
    bank_name = models.CharField(max_length=64, blank=True)
    account_number_encrypted = models.CharField(max_length=255, blank=True, null=True)
    account_holder = models.CharField(max_length=64, blank=True)   # 예금주
    # 자격/차량
    license_number = models.CharField(max_length=64, blank=True)   # 운전면허 번호
    vehicle_number = models.CharField(max_length=32, blank=True)   # 차량 번호
    # 운행 조건
    region = models.CharField(max_length=128, blank=True)          # 희망 배달 지역
    delivery_method = models.CharField(
        max_length=16, choices=DeliveryMethod.choices, blank=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return f'RiderProfile(rider={self.rider_id}, {self.delivery_method})'


class RiderLocation(models.Model):
    """라이더의 실시간 현재 위치. 해킹커넥트(라이더 앱)가 운행 중 주기적으로 갱신한다.

    라이더당 한 행만 유지(OneToOne)하고 최신 좌표로 덮어쓴다. 이력이 필요하면
    별도 로그 테이블을 두는 편이 낫다(여기선 '지금 어디 있나'만 다룬다).
    """
    rider = models.OneToOneField(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='location',
    )
    latitude = models.FloatField()
    longitude = models.FloatField()
    accuracy = models.FloatField(null=True, blank=True)  # 위치 오차(m)
    # 서버가 관측한 누적 이동거리(km). 위치가 갱신될 때마다 직전 좌표와의 거리를
    # 더한다. 단, 순간이동(속도상한 초과)은 갱신 자체가 거부되므로 여기에 안 쌓인다.
    total_distance_km = models.FloatField(default=0)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return f'RiderLocation(rider={self.rider_id}, {self.latitude},{self.longitude})'


class TxnKey(models.Model):
    """[방어 ④] 사용자별 거래서명 공개키 (Keystore/TEE 하드웨어 키의 공개부).

    라이더 앱이 Android Keystore(EC P-256, StrongBox→TEE)에서 개인키를 생성하고
    공개키만 등록한다. 개인키는 하드웨어에 격리돼 앱조차 추출 불가하므로, 서버는
    이 공개키로만 '계좌 변경' 같은 민감 요청의 서명을 검증한다.

    → 커스텀 클라이언트(무루팅)는 개인키가 없어 유효 서명을 못 만든다(=401).
      유일한 우회는 루팅한 기기에서 살아있는 앱의 서명 함수를 후킹(서명 오라클)하는 것.
    """
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='txn_keys')
    key_id = models.CharField(max_length=64)
    public_key_pem = models.TextField()
    # [정산 fix③ 앵커] 등록 공개키 봉인값(서버 env 시크릿 HMAC). 등록 시 서버가 계산해
    # 저장한다. 검증 때 이 값을 다시 계산해 대조하므로, SQLi 로 public_key_pem 을
    # 공격자 키로 바꿔치기해도 봉인과 불일치하여 검증에서 걸러진다.
    reg_seal = models.CharField(max_length=64, blank=True, default='')
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ('user', 'key_id')

    def __str__(self):
        return f'TxnKey(user={self.user_id}, {self.key_id})'


class TxnNonce(models.Model):
    """[방어 ④] 1회성 nonce 기록(재전송 방지). 이미 쓰인 nonce 요청은 거부한다."""
    nonce = models.CharField(max_length=64, unique=True)
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return self.nonce


class DeliveryFeePolicy(models.Model):
    """배달비 산정 정책(단일행 싱글톤, id=1).

    배달료 = min(base_fee_krw + 거리km × fee_per_km, max_fee_krw).
    서버는 배달 완료 시점에 이 행을 '매번' 읽어 배달료를 산정한다(캐시하지 않음).

    [의도된 취약점 연계] 관리자 검색 API(adminpanel)의 SQLi(stacked query)로
    이 행의 fee_per_km / max_fee_krw 를 UPDATE 하면 배달료 상한이 풀린다. 이후
    GPS 위조로 거리를 부풀리면 배달료가 무제한으로 커진다. db_table 을
    'delivery_fee_policy' 로 고정해 SQLi 페이로드가 테이블명을 그대로 쓸 수 있게 한다.
    """
    base_fee_krw = models.PositiveIntegerField(default=3000)   # 기본료
    fee_per_km = models.PositiveIntegerField(default=1000)     # km당 요금
    max_fee_krw = models.PositiveIntegerField(default=50000)   # 배달료 상한(cap)

    class Meta:
        db_table = 'delivery_fee_policy'

    def __str__(self):
        return (f'DeliveryFeePolicy(base={self.base_fee_krw}, '
                f'per_km={self.fee_per_km}, max={self.max_fee_krw})')

    @classmethod
    def get_solo(cls):
        """정책 단일행(id=1)을 반환한다(없으면 기본값으로 생성).

        매 호출마다 DB 를 조회하므로, SQLi 로 바뀐 값이 서버 재시작 없이 즉시
        반영된다(모듈/프로세스 캐시를 두지 않는 것이 취약점 시연의 핵심).
        """
        obj, _ = cls.objects.get_or_create(
            pk=1,
            defaults={'base_fee_krw': 3000, 'fee_per_km': 1000, 'max_fee_krw': 50000},
        )
        return obj


class RiderPayout(models.Model):
    """정산 배치가 라이더에게 실제 '지급'한 기록(= 돈이 계좌로 나간 시점).

    지급 대상 계좌는 **지급 시점의 RiderProfile 저장 계좌**를 그대로 읽는다.

    [의도된 취약점 연계] IDOR(rider_account_by_id, 소유권 미검사)로 피해자 라이더의
    정산계좌가 공격자 것으로 바뀌어 있으면, 배치가 피해자의 '정직하게 번' 정산금을
    **공격자 계좌로 지급**한다(계좌 스왑 → 배치 지급 → 절도). 지급 금액은 5-fix 재검증
    (서명·거리 도장)을 통과한 배달만 합산하므로, SQLi 로 부풀린 위조 정산은 지급되지 않는다.
    """
    class Status(models.TextChoices):
        PAID = 'paid', 'Paid'

    rider = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='payouts')
    amount = models.PositiveIntegerField(default=0)
    dest_bank_name = models.CharField(max_length=64, blank=True)      # 지급 시점 계좌(스냅샷)
    dest_account_masked = models.CharField(max_length=64, blank=True)
    dest_account_holder = models.CharField(max_length=64, blank=True)
    status = models.CharField(max_length=16, choices=Status.choices, default=Status.PAID)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f'RiderPayout(rider={self.rider_id}, {self.amount}→{self.dest_account_masked})'
