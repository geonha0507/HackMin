"""Location endpoints (/api/v1/locations). Require authentication."""

import math

from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response

from common.exceptions import error_response
from .models import Restaurant
from .serializers import RestaurantListSerializer

# Minimal mock address book (외부 지오코딩 대신 데모용 고정 데이터).
_MOCK_PLACES = [
    {'address': '서울특별시 강남구 테헤란로 152', 'latitude': 37.5006, 'longitude': 127.0366},
    {'address': '서울특별시 마포구 양화로 45', 'latitude': 37.5561, 'longitude': 126.9236},
    {'address': '서울특별시 송파구 올림픽로 300', 'latitude': 37.5145, 'longitude': 127.1059},
    {'address': '부산광역시 해운대구 해운대해변로 264', 'latitude': 35.1587, 'longitude': 129.1604},
]


@api_view(['GET'])
@permission_classes([IsAuthenticated])
def location_search(request):
    """주소 검색. ?q= 로 mock 주소 목록을 필터링한다."""
    q = request.query_params.get('q', '').strip()
    results = [p for p in _MOCK_PLACES if q in p['address']] if q else _MOCK_PLACES
    return Response({'count': len(results), 'results': results})


def _haversine_km(lat1, lng1, lat2, lng2):
    r = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlmb = math.radians(lng2 - lng1)
    a = math.sin(dphi / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlmb / 2) ** 2
    return r * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


@api_view(['GET'])
@permission_classes([IsAuthenticated])
def nearby_restaurants(request):
    """현재 위치(lat,lng) 기반 주변 음식점. ?lat=&lng=&radius_km=(기본 3)."""
    try:
        lat = float(request.query_params['lat'])
        lng = float(request.query_params['lng'])
    except (KeyError, ValueError):
        return error_response('bad_request', 'lat, lng 파라미터가 필요합니다.', 400)
    try:
        radius = float(request.query_params.get('radius_km', 3))
    except ValueError:
        return error_response('bad_request', 'radius_km은 숫자여야 합니다.', 400)

    results = []
    for r in Restaurant.objects.exclude(latitude__isnull=True).exclude(longitude__isnull=True):
        dist = _haversine_km(lat, lng, r.latitude, r.longitude)
        if dist <= radius:
            data = RestaurantListSerializer(r).data
            data['distance_km'] = round(dist, 2)
            results.append(data)
    results.sort(key=lambda x: x['distance_km'])
    return Response({'count': len(results), 'results': results})
