"""점주 소유 매장 조회 (/api/v1/owner/restaurants).

web_bff(점주 웹 BFF)가 대시보드의 '내 매장' 패널을 그리기 위해 필요하다.
기존에는 웹이 restaurants.selectors.owned_restaurants() 를 ORM 으로 직접
호출했지만, DB에 붙지 않는 컨테이너에서는 API 가 있어야 한다.

응답은 점주 본인 소유 매장만 담으므로 address 까지 포함한 Detail 시리얼라이저를
쓴다 (공개 목록용 RestaurantListSerializer 에는 address 가 없다).
"""

from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from common.permissions import IsOwner
from restaurants.selectors import owned_restaurants
from restaurants.serializers import RestaurantDetailSerializer


@api_view(['GET'])
@permission_classes([IsOwner])
def owner_restaurant_list(request):
    """로그인한 점주가 소유한 매장 목록."""
    qs = owned_restaurants(request.user)
    return Response({'results': RestaurantDetailSerializer(qs, many=True).data})
