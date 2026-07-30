"""Notice endpoints. Public read (any authenticated user) + admin CRUD (/api/v1/admin/notices)."""

from rest_framework.decorators import api_view, parser_classes, permission_classes
from rest_framework.parsers import FormParser, JSONParser, MultiPartParser
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response

from common.exceptions import error_response
from common.permissions import IsAdminRole
from .models import Notice
from .serializers import NoticeSerializer


# --- Public read (customer/owner/rider 앱 공용) ------------------------------
@api_view(['GET'])
@permission_classes([IsAuthenticated])
def notice_list(request):
    notices = Notice.objects.all()
    return Response({'results': NoticeSerializer(notices, many=True).data})


@api_view(['GET'])
@permission_classes([IsAuthenticated])
def notice_detail(request, pk):
    notice = Notice.objects.filter(pk=pk).first()
    if not notice:
        return error_response('not_found', '공지사항을 찾을 수 없습니다.', 404)
    return Response(NoticeSerializer(notice).data)


# --- Admin CRUD --------------------------------------------------------------
@api_view(['GET', 'POST'])
@permission_classes([IsAdminRole])
@parser_classes([MultiPartParser, FormParser, JSONParser])
def admin_notice_list_create(request):
    if request.method == 'POST':
        serializer = NoticeSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        serializer.save(created_by=request.user)
        return Response(serializer.data, status=201)
    notices = Notice.objects.all()
    return Response({'results': NoticeSerializer(notices, many=True).data})


@api_view(['PUT', 'DELETE'])
@permission_classes([IsAdminRole])
@parser_classes([MultiPartParser, FormParser, JSONParser])
def admin_notice_detail(request, pk):
    notice = Notice.objects.filter(pk=pk).first()
    if not notice:
        return error_response('not_found', '공지사항을 찾을 수 없습니다.', 404)
    if request.method == 'DELETE':
        notice.delete()
        return Response(status=204)
    serializer = NoticeSerializer(notice, data=request.data, partial=True)
    serializer.is_valid(raise_exception=True)
    serializer.save()
    return Response(serializer.data)
