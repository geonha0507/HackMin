"""점주 소유 매장 조회 (/api/v1/owner/restaurants).

web_bff(점주 웹 BFF)가 대시보드의 '내 매장' 패널과 상품·카테고리 화면의 매장
선택 드롭다운을 그리기 위해 필요하다. 기존에는 웹이
restaurants.selectors.owned_restaurants() 를 ORM 으로 직접 호출했지만, DB에
붙지 않는 컨테이너에서는 API 가 있어야 한다.

응답에는 승인 상태(is_reviewable)가 포함된다. 상품·카테고리 등록 폼에서
심사 대기/반려 매장을 걸러내는 데 쓴다.
"""

from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response

from common.permissions import IsOwner
from restaurants.selectors import owned_restaurants

from .serializers import OwnerRestaurantSerializer


@api_view(['GET'])
@permission_classes([IsOwner])
def owner_restaurant_list(request):
    """로그인한 점주가 소유한 매장 목록.

    ?reviewable=1 이면 승인 완료된 매장만 반환한다.
    """
    qs = owned_restaurants(request.user).select_related('owner', 'enrollment_request')
    data = OwnerRestaurantSerializer(qs, many=True).data
    if request.query_params.get('reviewable') == '1':
        data = [r for r in data if r['is_reviewable']]
    return Response({'results': data})
