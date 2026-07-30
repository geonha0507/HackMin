"""점주 소유 매장 목록/추가 (/api/v1/owner/restaurants).

GET  : web_bff 의 대시보드·상품·매출 화면이 쓰는 매장 목록.
POST : 매장 추가 (사업자등록증 multipart). 실제 처리는 store.create_restaurant.

두 메서드가 같은 경로를 쓰므로 한 뷰에서 분기한다. urls.py 에 같은 path 를
두 번 등록하면 앞선 것만 매칭돼 POST 가 405 로 떨어진다.
"""

from rest_framework.decorators import api_view, parser_classes, permission_classes
from rest_framework.parsers import FormParser, JSONParser, MultiPartParser
from rest_framework.response import Response

from common.permissions import IsOwner
from restaurants.selectors import owned_restaurants

from . import store
from .serializers import OwnerRestaurantSerializer


@api_view(['GET', 'POST'])
@permission_classes([IsOwner])
@parser_classes([MultiPartParser, FormParser, JSONParser])
def owner_restaurant_list_create(request):
    """GET: 소유 매장 목록 (?reviewable=1 이면 승인 완료된 것만).
    POST: 매장 추가.
    """
    if request.method == 'POST':
        return store.create_restaurant(request)

    qs = owned_restaurants(request.user).select_related('owner', 'enrollment_request')
    data = OwnerRestaurantSerializer(qs, many=True).data
    if request.query_params.get('reviewable') == '1':
        data = [r for r in data if r['is_reviewable']]
    return Response({'results': data})
