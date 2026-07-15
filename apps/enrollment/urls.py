"""Enrollment app URLs."""

from django.urls import path
from . import views

urlpatterns = [
    # Owner: 입점 요청 제출
    path('enrollment/submit', views.submit_enrollment_request, name='submit_enrollment'),
    
    # Admin: 입점 요청 관리
    path('enrollment/list', views.list_enrollment_requests, name='list_enrollments'),
    path('enrollment/<int:request_id>', views.get_enrollment_request, name='get_enrollment'),
    path('enrollment/<int:request_id>/review', views.review_enrollment_request, name='review_enrollment'),
]
