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

    def __str__(self):
        return f'Delivery(order={self.order_id}, {self.status})'


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
