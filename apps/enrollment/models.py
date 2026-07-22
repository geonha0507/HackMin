"""입점 요청 모델."""

from django.db import models
#from django.contrib.auth.models import User
from django.conf import settings


class EnrollmentRequest(models.Model):
    """입점 요청 (Owner → Admin 승인 대기)."""
    restaurant = models.OneToOneField(
        'restaurants.Restaurant',
        on_delete=models.SET_NULL,
        related_name='enrollment_request',
        null=True,
        blank=True,
    )
    STATUS_CHOICES = [
        ('pending', 'Pending'),
        ('approved', 'Approved'),
        ('rejected', 'Rejected'),
    ]
    
    # Owner 정보
    username = models.CharField(max_length=150)
    password = models.CharField(max_length=255)  # Hashed
    phone = models.CharField(max_length=20)
    owner_name = models.CharField(max_length=100)  # 점주명
    restaurant_name = models.CharField(max_length=200)  # 가게명
    business_license = models.FileField(upload_to='enrollment_licenses/')
    
    # 상태 추적
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='pending')
    created_at = models.DateTimeField(auto_now_add=True)
    reviewed_at = models.DateTimeField(null=True, blank=True)
    #reviewed_by = models.ForeignKey(User, on_delete=models.SET_NULL, null=True, blank=True, related_name='reviewed_enrollments')
    reviewed_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name='reviewed_enrollments'
    )
    rejection_reason = models.TextField(null=True, blank=True)
    
    class Meta:
        ordering = ['-created_at']
    
    def __str__(self):
        return f"{self.restaurant_name} ({self.status})"
