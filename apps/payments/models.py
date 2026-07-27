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


class PaymentCard(models.Model):
    """사용자가 등록한 결제 카드. 카드번호는 AES-256 암호화해 저장하고, 화면 표시는 마스킹값을 쓴다.
    CVC/비밀번호는 보관하지 않는다(실무 관례)."""
    user = models.ForeignKey('accounts.User', on_delete=models.CASCADE, related_name='payment_cards')
    provider = models.CharField(max_length=16, default='card')  # card | kakao | naver
    card_number_encrypted = models.TextField()          # AES-256(카드번호 전체)
    card_masked = models.CharField(max_length=32)         # 예: ****-****-****-1234
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f'PaymentCard({self.user_id}, {self.card_masked})'


class BankAccount(models.Model):
    """사용자가 등록한 계좌. 계좌번호는 AES-256 암호화해 저장하고, 화면 표시는 마스킹값을 쓴다.
    계좌 비밀번호는 보관하지 않는다."""
    user = models.ForeignKey('accounts.User', on_delete=models.CASCADE, related_name='bank_accounts')
    bank = models.CharField(max_length=32)                # 은행명
    account_number_encrypted = models.TextField()          # AES-256(계좌번호 전체)
    account_masked = models.CharField(max_length=32)       # 예: ******7890
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f'BankAccount({self.user_id}, {self.bank} {self.account_masked})'
