"""Enrollment app Django Admin configuration."""

from django.contrib import admin
from .models import EnrollmentRequest


@admin.register(EnrollmentRequest)
class EnrollmentRequestAdmin(admin.ModelAdmin):
    list_display = ['restaurant_name', 'owner_name', 'phone', 'status', 'created_at', 'reviewed_by']
    list_filter = ['status', 'created_at']
    search_fields = ['username', 'restaurant_name', 'owner_name', 'phone']
    readonly_fields = ['created_at', 'reviewed_at', 'reviewed_by', 'business_license']
    
    fieldsets = (
        ('기본 정보', {
            'fields': ('username', 'password', 'owner_name', 'restaurant_name', 'phone')
        }),
        ('서류', {
            'fields': ('business_license',)
        }),
        ('상태 관리', {
            'fields': ('status', 'rejection_reason', 'created_at', 'reviewed_at', 'reviewed_by')
        }),
    )
    
    def has_add_permission(self, request):
        """Admin에서 직접 생성 불허 (API로만 제출 가능)."""
        return False
