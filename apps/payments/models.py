import uuid

from django.db import models


def generate_transaction_id():
    return 'TXN' + uuid.uuid4().hex[:20].upper()


class Payment(models.Model):
    class Method(models.TextChoices):
        CARD = 'card', 'Card'
        EASY_PAY = 'easy_pay', 'Easy pay'
        MOCK = 'mock', 'Mock'

    class Status(models.TextChoices):
        PENDING = 'pending', 'Pending'
        PAID = 'paid', 'Paid'
        CANCELLED = 'cancelled', 'Cancelled'
        REFUNDED = 'refunded', 'Refunded'
        PARTIAL_REFUNDED = 'partial_refunded', 'Partially refunded'

    order = models.ForeignKey('orders.Order', on_delete=models.CASCADE, related_name='payments')
    method = models.CharField(max_length=16, choices=Method.choices, default=Method.MOCK)
    amount = models.PositiveIntegerField(default=0)
    status = models.CharField(max_length=20, choices=Status.choices, default=Status.PENDING)
    transaction_id = models.CharField(max_length=32, unique=True, default=generate_transaction_id)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return self.transaction_id


class Refund(models.Model):
    class Status(models.TextChoices):
        REQUESTED = 'requested', 'Requested'
        COMPLETED = 'completed', 'Completed'
        REJECTED = 'rejected', 'Rejected'

    payment = models.ForeignKey(Payment, on_delete=models.CASCADE, related_name='refunds')
    amount = models.PositiveIntegerField(default=0)
    reason = models.CharField(max_length=255, blank=True)
    status = models.CharField(max_length=16, choices=Status.choices, default=Status.REQUESTED)
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f'Refund({self.payment_id}, {self.amount})'
