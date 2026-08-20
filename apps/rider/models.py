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
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return f'RiderLocation(rider={self.rider_id}, {self.latitude},{self.longitude})'
