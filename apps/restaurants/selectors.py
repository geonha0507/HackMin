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
