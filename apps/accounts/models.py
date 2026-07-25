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
    rrn_encrypted = models.CharField(
        max_length=255, blank=True, null=True,
        help_text="주민등록번호 (AES-128 암호화)"
    )
    rrn_hash = models.CharField(
        max_length=64, unique=True, null=True, blank=True,
        help_text="주민등록번호 중복 확인용 해시(SHA-256). 복호화 불가능해서 이 값만으로는 원본을 알 수 없다.",
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
