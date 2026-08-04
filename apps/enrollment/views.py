"""입점 요청 API 엔드포인트."""

import os
from datetime import datetime
from django.contrib.auth import get_user_model

User = get_user_model()
from django.contrib.auth.hashers import make_password

from rest_framework.decorators import api_view, parser_classes, permission_classes
from rest_framework.parsers import FormParser, MultiPartParser
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response

from common.exceptions import error_response
from restaurants.models import Restaurant
from .models import EnrollmentRequest
from .serializers import (
    EnrollmentRequestSerializer,
    EnrollmentRequestAdminSerializer,
    EnrollmentReviewSerializer,
)

_ALLOWED_DOC_EXT = {'.pdf', '.jpg', '.jpeg', '.png'}
_MAX_DOC_BYTES = 10 * 1024 * 1024


# ============================================================================
# Permissions
# ============================================================================

class IsAdmin(IsAuthenticated):
    """Admin(staff) 권한만 허용."""
    
    def has_permission(self, request, view):
        return super().has_permission(request, view) and request.user.is_staff


# ============================================================================
# Enrollment Request API
# ============================================================================

@api_view(['POST'])
@parser_classes([MultiPartParser, FormParser])
@permission_classes([AllowAny])
def submit_enrollment_request(request):
    """입점 요청 제출 (Owner).
    
    Required:
    - username
    - password
    - phone
    - owner_name (점주명)
    - restaurant_name (가게명)
    - business_license (파일: pdf, jpg, jpeg, png / 최대 10MB)
    
    파일 확장자 + 크기 검증 포함.
    """
    # 필수 필드 확인
    required_fields = ['username', 'password', 'phone', 'owner_name', 
                      'restaurant_name', 'business_license']
    missing = [f for f in required_fields if f not in request.data and f not in request.FILES]
    
    if missing:
        return error_response('bad_request', f'필수 항목 누락: {", ".join(missing)}', 400)
    
    # Serializer 검증
    serializer = EnrollmentRequestSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    # username 중복 확인
    if EnrollmentRequest.objects.filter(username=request.data['username']).exists():
        return error_response('duplicate_username', '이미 등록된 사용자명입니다.', 400)
    
    if User.objects.filter(username=request.data['username']).exists():
        return error_response('duplicate_username', '이미 사용 중인 사용자명입니다.', 400)
    
    # 입점 요청 생성
    obj = EnrollmentRequest.objects.create(
        username=request.data['username'],
        password=make_password(request.data['password']),  # 비밀번호 해싱
        phone=request.data['phone'],
        owner_name=request.data['owner_name'],
        restaurant_name=request.data['restaurant_name'],
        business_license=request.FILES['business_license']
    )
    
    return Response(
        EnrollmentRequestSerializer(obj).data,
        status=201
    )


@api_view(['GET'])
@permission_classes([IsAdmin])
def list_enrollment_requests(request):
    """Admin: 입점 요청 목록 조회.
    
    Query params:
    - status: pending (기본값) | approved | rejected | all
    """
    status = request.query_params.get('status', 'pending')
    
    if status == 'all':
        queryset = EnrollmentRequest.objects.all()
    else:
        queryset = EnrollmentRequest.objects.filter(status=status)
    
    serializer = EnrollmentRequestAdminSerializer(queryset, many=True)
    return Response(serializer.data)


@api_view(['GET'])
@permission_classes([IsAdmin])
def get_enrollment_request(request, request_id):
    """Admin: 입점 요청 상세 조회."""
    try:
        enrollment = EnrollmentRequest.objects.get(id=request_id)
    except EnrollmentRequest.DoesNotExist:
        return error_response('not_found', '요청을 찾을 수 없습니다.', 404)
    
    serializer = EnrollmentRequestAdminSerializer(enrollment)
    return Response(serializer.data)


@api_view(['POST'])
@permission_classes([IsAdmin])
def review_enrollment_request(request, request_id):
    """Admin: 입점 요청 승인/거절.
    
    Request body:
    {
        "status": "approved" | "rejected",
        "rejection_reason": "거절 사유 (거절 시만 필수)"
    }
    """
    try:
        enrollment = EnrollmentRequest.objects.get(id=request_id)
    except EnrollmentRequest.DoesNotExist:
        return error_response('not_found', '요청을 찾을 수 없습니다.', 404)
    
    # 이미 처리된 요청
    if enrollment.status != 'pending':
        return error_response('already_reviewed', f'이미 {enrollment.status} 처리된 요청입니다.', 400)
    
    serializer = EnrollmentReviewSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    
    status = serializer.validated_data['status']
    
    # ===== 승인 =====
    if status == 'approved':
        # User 생성
        user = User.objects.create_user(
            username=enrollment.username,
            password=None,
        )
        user.password = enrollment.password  # submit 시 make_password()로 이미 해싱됨
        user.save(update_fields=['password'])
        
        # Restaurant 생성
        restaurant = Restaurant.objects.create(
            owner=user,
            name=enrollment.restaurant_name,
            phone=enrollment.phone,
            business_license=enrollment.business_license,
            is_open=True
        )
        
        enrollment.status = 'approved'
    
    # ===== 거절 =====
    elif status == 'rejected':
        reason = serializer.validated_data.get('rejection_reason', '')
        if not reason:
            return error_response('bad_request', '거절 사유는 필수입니다.', 400)
        enrollment.status = 'rejected'
        enrollment.rejection_reason = reason
    
    enrollment.reviewed_at = datetime.now()
    enrollment.reviewed_by = request.user
    enrollment.save()
    
    return Response(
        EnrollmentRequestAdminSerializer(enrollment).data,
        status=200
    )
