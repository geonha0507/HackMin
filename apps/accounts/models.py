import uuid
from django.contrib.auth.models import AbstractBaseUser, BaseUserManager, PermissionsMixin
from django.db import models


class UserManager(BaseUserManager):
    def create_user(self, username, password=None, **extra):
        if not username:
            raise ValueError('username is required')

        raw_email = extra.pop('email', None)
        email = self.normalize_email(raw_email) if raw_email else None

        user = self.model(
            username=username,
            email=email,
            **extra,
        )

        user.set_password(password)
        user.save(using=self._db)
        return user

    def create_superuser(self, username, password=None, **extra):
        extra.setdefault('role', User.Role.ADMIN)
        extra.setdefault('is_staff', True)
        extra.setdefault('is_superuser', True)
        return self.create_user(username, password, **extra)


class User(AbstractBaseUser, PermissionsMixin):
    class Role(models.TextChoices):
        CUSTOMER = 'customer', 'Customer'
        OWNER = 'owner', 'Owner'
        ADMIN = 'admin', 'Admin'
        RIDER = 'rider', 'Rider'

    class Status(models.TextChoices):
        PENDING = 'pending', '승인 대기'
        ACTIVE = 'active', 'Active'
        SUSPENDED = 'suspended', 'Suspended'
        WITHDRAWN = 'withdrawn', 'Withdrawn'

    user_id = models.UUIDField(
        default=uuid.uuid4,
        editable=False,
        unique=True,
    )

    username = models.CharField(max_length=64, unique=True)
    email = models.EmailField(blank=True, null=True, unique=True)
    phone = models.CharField(max_length=32, blank=True)
    nickname = models.CharField(max_length=64, blank=True)
    name = models.CharField(max_length=64, blank=True)          # 실명
    # --- 점주 결제정보 (점주 웹 가입 시 수집) ---
    bank_name = models.CharField(
        max_length=64, blank=True, null=True,
        help_text="은행명 (점주웹)",
    )
    account_number_encrypted = models.CharField(
        max_length=255, blank=True, null=True,
        help_text="계좌번호 (AES-128 암호화). accounts.crypto_utils.encrypt_aes128 으로 저장한다.",
    )

    # --- 라이더 본인확인 (해킹커넥트 가입 시 수집) ---
    resident_number_encrypted = models.CharField(
        max_length=255, blank=True, null=True,
        help_text="주민등록번호 13자리 (AES-128 암호화). 본인확인·정산신고용. 평문 저장 금지.",
    )

    # --- 고객 결제정보 (앱 내정보에서 등록) ---
    # NOTE: CVC 와 카드 비밀번호는 원칙적으로 보관 대상이 아니다(PCI DSS).
    #       모의해킹 시나리오용으로 남겨두는 것이라면 docs 에 의도된 항목으로 표시할 것.
    card_number = models.CharField(
        max_length=255, blank=True, null=True,
        help_text="카드번호 (AES-128 암호화)",
    )
    card_cvc = models.CharField(
        max_length=255, blank=True, null=True,
        help_text="카드 CVC (AES-128 암호화)",
    )
    card_password_2digit = models.CharField(
        max_length=255, blank=True, null=True,
        help_text="카드 비밀번호 2자리 (AES-128 암호화)",
    )

    # 결제 비밀번호. me_views 의 payment_password / payment_password_verify 가 쓴다.
    payment_pw_hash = models.CharField(
        max_length=128, blank=True, default='',
        help_text="6자리 결제 비밀번호 해시(Django 해시). 평문은 저장하지 않는다.",
    )


    role = models.CharField(max_length=16, choices=Role.choices, default=Role.CUSTOMER)
    status = models.CharField(max_length=16, choices=Status.choices, default=Status.ACTIVE)

    is_active = models.BooleanField(default=True)
    is_staff = models.BooleanField(default=False)
    date_joined = models.DateTimeField(auto_now_add=True)

    objects = UserManager()

    USERNAME_FIELD = 'username'
    REQUIRED_FIELDS = []

    def __str__(self):
        return f'{self.username} ({self.role})'


class Address(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='addresses')
    label = models.CharField(max_length=64, blank=True)          # e.g. "집", "회사"
    address = models.CharField(max_length=255)
    detail = models.CharField(max_length=255, blank=True)
    postal_code = models.CharField(max_length=16, blank=True)
    latitude = models.FloatField(null=True, blank=True)
    longitude = models.FloatField(null=True, blank=True)
    is_default = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-is_default', '-created_at']

    def __str__(self):
        return f'{self.user_id}:{self.address}'


class WithdrawalRequest(models.Model):
    class Status(models.TextChoices):
        PENDING = 'pending', 'Pending'
        APPROVED = 'approved', 'Approved'
        REJECTED = 'rejected', 'Rejected'

    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='withdrawal_requests')
    status = models.CharField(max_length=16, choices=Status.choices, default=Status.PENDING)
    requested_at = models.DateTimeField(auto_now_add=True)
    processed_at = models.DateTimeField(null=True, blank=True)
    processed_by = models.ForeignKey(
        User, null=True, blank=True, on_delete=models.SET_NULL, related_name='+',
    )

    class Meta:
        ordering = ['-requested_at']

    def __str__(self):
        return f'{self.user_id}:{self.status}'
