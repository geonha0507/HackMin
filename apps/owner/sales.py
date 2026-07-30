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


def _scope(request):
    """기간·매장·상태로 좁힌 주문 쿼리셋과 적용된 매장 목록을 돌려준다.

    이전에는 sales_summary 가 _SALES_STATUSES 를 선언만 하고 쓰지 않아
    취소·거절 주문까지 매출에 합산됐다. 웹 화면(apps/web)은 자체적으로
    걸러냈기 때문에 같은 지표가 화면과 API 에서 서로 달랐다.
    """
    owned = _owned_ids(request.user)

    restaurant_id = request.query_params.get('restaurant_id')
    if restaurant_id:
        try:
            rid = int(restaurant_id)
        except (TypeError, ValueError):
            rid = None
        # 소유하지 않은 매장 id 는 무시한다 (IDOR 차단).
        owned = [rid] if rid in owned else owned

    qs = Order.objects.filter(restaurant_id__in=owned, status__in=_SALES_STATUSES)

    start = request.query_params.get('start', '')
    end = request.query_params.get('end', '')
    if start:
        qs = qs.filter(created_at__date__gte=start)
    if end:
        qs = qs.filter(created_at__date__lte=end)
    return qs, owned


@api_view(['GET'])
@permission_classes([IsOwner])
def sales_summary(request):
    """일별 매출. ?start=YYYY-MM-DD&end=YYYY-MM-DD&restaurant_id=N

    매출로 집계하는 상태는 _SALES_STATUSES 로 고정한다(취소·거절 제외).
    """
    qs, owned = _scope(request)
    if not owned:
        return Response({'total_sales': 0, 'order_count': 0, 'daily': []})

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
    """메뉴별 매출. sales_summary 와 같은 필터(기간·매장·상태)를 받는다.

    ?limit=N 으로 상위 N 개만 (기본 20, 최대 100).
    """
    qs, _owned = _scope(request)
    try:
        limit = min(max(int(request.query_params.get('limit', 20)), 1), 100)
    except (TypeError, ValueError):
        limit = 20

    agg = (
        OrderItem.objects.filter(order__in=qs)
        .values('menu_name')
        .annotate(quantity=Sum('quantity'), sales=Sum('line_total'))
        .order_by('-sales')[:limit]
    )
    return Response({'results': [a for a in agg if a['menu_name']]})


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
