"""Owner sales analytics & export (/api/v1/owner/sales...)."""

import csv

from django.db.models import Count, Sum
from django.http import HttpResponse
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from common.permissions import IsOwner
from orders.models import Order, OrderItem
from restaurants.selectors import owned_restaurant_ids

# 매출로 집계할 주문 상태.
_SALES_STATUSES = (Order.Status.DELIVERED, Order.Status.DELIVERING, Order.Status.PLACED,
                   Order.Status.ACCEPTED, Order.Status.COOKING, Order.Status.COOKED)


def _owned_ids(user):
    return owned_restaurant_ids(user)


@api_view(['GET'])
@permission_classes([IsOwner])
def sales_summary(request):
    """일/주/월별 매출. ?start=YYYY-MM-DD&end=YYYY-MM-DD (ORM 파라미터 바인딩, Secure 고정)."""
    start = request.query_params.get('start', '')
    end = request.query_params.get('end', '')
    owned = _owned_ids(request.user)
    if not owned:
        return Response({'total_sales': 0, 'order_count': 0, 'daily': []})

    qs = Order.objects.filter(restaurant_id__in=owned)
    if start:
        qs = qs.filter(created_at__date__gte=start)
    if end:
        qs = qs.filter(created_at__date__lte=end)
    agg = (
        qs.values('created_at__date')
        .annotate(order_count=Count('id'), sales=Sum('total'))
        .order_by('created_at__date')
    )
    daily = [
        {'date': str(a['created_at__date']), 'order_count': a['order_count'],
         'sales': a['sales'] or 0}
        for a in agg
    ]

    return Response({
        'total_sales': sum(d['sales'] for d in daily),
        'order_count': sum(d['order_count'] for d in daily),
        'daily': daily,
    })


@api_view(['GET'])
@permission_classes([IsOwner])
def sales_by_menu(request):
    owned = _owned_ids(request.user)
    agg = (
        OrderItem.objects.filter(order__restaurant_id__in=owned)
        .values('menu_name')
        .annotate(quantity=Sum('quantity'), sales=Sum('line_total'))
        .order_by('-sales')
    )
    return Response({'results': list(agg)})


@api_view(['GET'])
@permission_classes([IsOwner])
def sales_stats(request):
    owned = _owned_ids(request.user)
    agg = (
        Order.objects.filter(restaurant_id__in=owned)
        .values('status')
        .annotate(count=Count('id'), sales=Sum('total'))
    )
    return Response({'by_status': list(agg)})


def _sanitize_cell(value):
    """CSV formula injection 방지: 위험 문자로 시작하면 작은따옴표를 앞에 붙인다."""
    text = str(value)
    if text and text[0] in ('=', '+', '-', '@'):
        return "'" + text
    return text


@api_view(['GET'])
@permission_classes([IsOwner])
def sales_export(request):
    """매출 데이터 CSV 다운로드. 위험 문자로 시작하는 셀을 이스케이프 (CSV Injection 방지, Secure 고정)."""
    owned = _owned_ids(request.user)
    orders = Order.objects.filter(restaurant_id__in=owned).order_by('-created_at')

    response = HttpResponse(content_type='text/csv')
    response['Content-Disposition'] = 'attachment; filename="sales.csv"'
    writer = csv.writer(response)
    writer.writerow(['order_number', 'status', 'total', 'request_note', 'created_at'])

    for o in orders:
        row = [o.order_number, o.status, o.total, o.request_note, o.created_at.isoformat()]
        row = [_sanitize_cell(c) for c in row]
        writer.writerow(row)
    return response
