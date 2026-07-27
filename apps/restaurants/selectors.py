"""점주(owner) 소유권 스코프 셀렉터.

'그 점주가 소유한 매장/메뉴'로 쿼리를 한정하는 로직을 한 곳에 모은다. 점주 API
(owner.*)와 관리 웹(web.views.owner)이 공유해 중복을 없앤다.
"""

from .models import Menu, Restaurant


def owned_restaurants(user):
    """해당 사용자가 소유한 매장 쿼리셋."""
    return Restaurant.objects.filter(owner=user)


def owned_restaurant_ids(user):
    """해당 사용자가 소유한 매장 id 리스트."""
    return list(owned_restaurants(user).values_list('id', flat=True))


def owns_restaurant(user, restaurant_id):
    """해당 매장이 이 사용자 소유인지 여부."""
    return owned_restaurants(user).filter(id=restaurant_id).exists()


def owned_menus(user):
    """해당 사용자 소유 매장의 메뉴 쿼리셋."""
    return Menu.objects.filter(restaurant__owner=user)


def is_reviewable_restaurant(restaurant):
    """이 매장이 정상 운영 가능(승인 완료) 상태인지.

    상품·카테고리 등록 가능 여부의 기준이다.
    - 점주가 나중에 추가한 매장은 EnrollmentRequest.status 로 판단한다.
    - 회원가입 시 만들어진 최초 매장은 EnrollmentRequest 가 없을 수 있어
      소유자 계정 상태로 판단한다.

    NOTE: apps/web/views/restaurant.py 에 같은 로직의 사본이 있다. 그쪽은
    web_bff 이관이 끝나면 제거될 예정이라 당분간 중복을 허용한다.
    """
    enrollment = getattr(restaurant, 'enrollment_request', None)
    if enrollment:
        return enrollment.status == 'approved'
    owner = restaurant.owner
    return bool(owner) and owner.status == owner.Status.ACTIVE
