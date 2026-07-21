"""File download endpoints (/api/v1/downloads) — File Download 실습 대상.

주요 취약점:
- attachment: Path Traversal (사용자 입력 경로를 검증 없이 파일 시스템 접근)
- 나머지: IDOR (소유권 검증 없이 타인의 문서 다운로드)
"""

import os

from django.conf import settings
from django.db.models import Q
from django.http import FileResponse, HttpResponse
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response

from common.exceptions import error_response
from orders.models import Order
from restaurants.models import Restaurant

ATTACHMENT_ROOT = os.path.join(settings.MEDIA_ROOT, 'attachments')


def _text_download(filename, body):
    response = HttpResponse(body, content_type='text/plain; charset=utf-8')
    response['Content-Disposition'] = f'attachment; filename="{filename}"'
    return response


def _order_for(request, order_id):
    """Secure: 본인 주문 또는 본인 매장 주문."""
    user = request.user
    return Order.objects.filter(pk=order_id).filter(
        Q(user=user) | Q(restaurant__owner=user)
    ).first()


@api_view(['GET'])
@permission_classes([IsAuthenticated])
def receipt(request, order_id):
    """영수증 다운로드."""
    order = _order_for(request, order_id)
    if not order:
        return error_response('not_found', '주문을 찾을 수 없습니다.', 404)
    lines = [
        '=== 영수증 ===',
        f'주문번호: {order.order_number}',
        f'상태: {order.status}',
        f'주소: {order.address} {order.address_detail}',
        '',
    ]
    for item in order.items.all():
        lines.append(f'{item.menu_name} x{item.quantity} = {item.line_total}원')
    lines += ['', f'상품합계: {order.subtotal}원', f'배달비: {order.delivery_fee}원',
              f'할인: {order.discount}원', f'결제금액: {order.total}원']
    return _text_download(f'receipt_{order.order_number}.txt', '\n'.join(lines))


@api_view(['GET'])
@permission_classes([IsAuthenticated])
def order_history(request, pk):
    """주문 내역 다운로드."""
    if str(request.user.id) != str(pk):
        return error_response('forbidden', '본인 주문 내역만 다운로드할 수 있습니다.', 403)
    orders = Order.objects.filter(user_id=pk)
    lines = ['=== 주문 내역 ===']
    for o in orders.order_by('-created_at'):
        lines.append(f'{o.created_at:%Y-%m-%d} {o.order_number} {o.status} {o.total}원')
    return _text_download(f'order_history_{pk}.txt', '\n'.join(lines))


@api_view(['GET'])
@permission_classes([IsAuthenticated])
def sales_report(request, pk):
    """매출 보고서 다운로드(점주)."""
    restaurant = Restaurant.objects.filter(pk=pk, owner=request.user).first()
    if not restaurant:
        return error_response('not_found', '매장을 찾을 수 없습니다.', 404)
    orders = Order.objects.filter(restaurant=restaurant)
    total = sum(o.total for o in orders)
    lines = [
        f'=== 매출 보고서: {restaurant.name} ===',
        f'총 주문 수: {orders.count()}',
        f'총 매출: {total}원',
    ]
    return _text_download(f'sales_report_{pk}.txt', '\n'.join(lines))


@api_view(['GET'])
@permission_classes([IsAuthenticated])
def business_license(request, pk):
    """사업자등록증 다운로드(점주/관리자)."""
    user = request.user
    is_admin = getattr(user, 'role', None) == 'admin' or user.is_staff
    if is_admin:
        restaurant = Restaurant.objects.filter(pk=pk).first()
    else:
        restaurant = Restaurant.objects.filter(pk=pk, owner=user).first()
    if not restaurant or not restaurant.business_license:
        return error_response('not_found', '사업자등록증을 찾을 수 없습니다.', 404)
    return FileResponse(restaurant.business_license.open('rb'), as_attachment=True)


@api_view(['GET'])
@permission_classes([IsAuthenticated])
def attachment(request, pk):
    """첨부파일 다운로드 — Path Traversal 실습.

    Secure 모드: 파일명 정규화 후 지정 디렉터리 밖 접근을 차단한다.
    """
    name = request.query_params.get('name', str(pk))

    # SECURE: basename만 사용하고 정규화된 경로가 기준 디렉터리 내부인지 확인.
    safe_name = os.path.basename(name)
    target = os.path.normpath(os.path.join(ATTACHMENT_ROOT, safe_name))
    if not target.startswith(os.path.normpath(ATTACHMENT_ROOT) + os.sep):
        return error_response('forbidden', '허용되지 않는 경로입니다.', 403)
    if not os.path.isfile(target):
        return error_response('not_found', '파일을 찾을 수 없습니다.', 404)
    return FileResponse(open(target, 'rb'), as_attachment=True)
