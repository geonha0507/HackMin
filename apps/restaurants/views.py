"""Public restaurant & menu browsing endpoints."""

from django.db import connection
from django.db.models import Q
from rest_framework import generics
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import AllowAny
from rest_framework.response import Response

from common.exceptions import error_response
from common.mode import is_vulnerable
from common.pagination import StandardPagination
from .models import Menu, Restaurant
from .serializers import (
    MenuDetailSerializer,
    MenuListSerializer,
    RestaurantDetailSerializer,
    RestaurantListSerializer,
    RestaurantReviewSerializer,
)

_SORT_FIELDS = {
    'rating': '-rating',
    'delivery_fee': 'delivery_fee',
    'min_order': 'min_order_amount',
    'newest': '-created_at',
}


@api_view(['GET'])
@permission_classes([AllowAny])
def restaurant_search(request):
    """🎯 음식점/음식 검색.

    지원 파라미터: q(음식명·음식점명), cuisine(종류), min_price, max_price,
    sort(rating|delivery_fee|min_order|newest).

    Vulnerable 모드는 q를 raw SQL에 문자열 결합하여 SQL Injection에 노출된다.
    """
    q = request.query_params.get('q', '')
    cuisine = request.query_params.get('cuisine', '')
    sort = request.query_params.get('sort', '')

    if is_vulnerable(request) and q:
        # VULNERABLE: 사용자 입력을 그대로 LIKE 절에 결합.
        # 예) q = "%' UNION SELECT ... -- " 형태로 인젝션 가능.
        sql = (
            "SELECT id FROM restaurants_restaurant "
            f"WHERE name LIKE '%{q}%' OR cuisine_type LIKE '%{q}%'"  # noqa
        )
        try:
            with connection.cursor() as cursor:
                cursor.execute(sql)
                ids = [row[0] for row in cursor.fetchall()]
        except Exception as exc:  # 인젝션 실습 시 오류 메시지 노출
            return error_response('query_error', str(exc), 400)
        queryset = Restaurant.objects.filter(id__in=ids)
    else:
        # SECURE: ORM 파라미터 바인딩.
        queryset = Restaurant.objects.all()
        if q:
            queryset = queryset.filter(
                Q(name__icontains=q)
                | Q(cuisine_type__icontains=q)
                | Q(menus__name__icontains=q)
            ).distinct()

    if cuisine:
        queryset = queryset.filter(cuisine_type__iexact=cuisine)

    min_price = request.query_params.get('min_price')
    max_price = request.query_params.get('max_price')
    if min_price is not None:
        try:
            min_price = int(min_price)
        except ValueError:
            return error_response('bad_request', 'min_price는 숫자여야 합니다.', 400)
        queryset = queryset.filter(menus__price__gte=min_price).distinct()
    if max_price is not None:
        try:
            max_price = int(max_price)
        except ValueError:
            return error_response('bad_request', 'max_price는 숫자여야 합니다.', 400)
        queryset = queryset.filter(menus__price__lte=max_price).distinct()

    queryset = queryset.order_by(_SORT_FIELDS.get(sort, '-rating'))

    paginator = StandardPagination()
    page = paginator.paginate_queryset(queryset, request)
    # context에 request를 넘겨야 ImageField(image)가 절대 URL로 직렬화된다.
    serializer = RestaurantListSerializer(page, many=True, context={'request': request})
    return paginator.get_paginated_response(serializer.data)


class RestaurantDetailView(generics.RetrieveAPIView):
    queryset = Restaurant.objects.all()
    serializer_class = RestaurantDetailSerializer
    permission_classes = [AllowAny]


class RestaurantMenuListView(generics.ListAPIView):
    serializer_class = MenuListSerializer
    permission_classes = [AllowAny]

    def get_queryset(self):
        return (
            Menu.objects.filter(restaurant_id=self.kwargs['pk'])
            .exclude(status=Menu.Status.HIDDEN)
        )


class MenuDetailView(generics.RetrieveAPIView):
    # HIDDEN 메뉴는 목록뿐 아니라 상세 조회에서도 제외 (id를 안다고 우회 노출되면 안 됨).
    queryset = (
        Menu.objects.exclude(status=Menu.Status.HIDDEN)
        .prefetch_related('option_groups__options')
    )
    serializer_class = MenuDetailSerializer
    permission_classes = [AllowAny]


class RestaurantReviewListView(generics.ListAPIView):
    serializer_class = RestaurantReviewSerializer
    permission_classes = [AllowAny]

    def get_queryset(self):
        from reviews.models import Review
        return (
            Review.objects.filter(restaurant_id=self.kwargs['pk'])
            .select_related('user', 'reply')
            .prefetch_related('images')
        )
