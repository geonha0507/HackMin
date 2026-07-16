"""마이페이지 > 내 매장 관리.

매장 정보 수정은 즉시 반영되지 않고 RestaurantEditRequest 로 접수되어
관리자 승인 후에만 실제 Restaurant 레코드에 반영된다.
"""

from django.contrib import messages
from django.shortcuts import redirect, render
from django.urls import reverse

from restaurants.models import Restaurant, RestaurantEditRequest
from ..decorators import owner_required

EDITABLE_FIELDS = [
    'name', 'cuisine_type', 'description', 'phone', 'address',
    'min_order_amount', 'delivery_fee', 'is_open',
]

CUISINE_PRESETS = ['한식', '중식', '양식', '일식', '분식', '치킨', '피자', '햄버거', '카페/디저트', '아시안']


def _split_cuisines(cuisine_type):
    return [c.strip() for c in cuisine_type.split(',') if c.strip()]


def _parse_form(post):
    try:
        min_order_amount = int(post.get('min_order_amount', '0') or 0)
        delivery_fee = int(post.get('delivery_fee', '0') or 0)
    except (TypeError, ValueError):
        min_order_amount = delivery_fee = -1

    checked = post.getlist('cuisine_type')
    custom = [c.strip() for c in post.getlist('custom_cuisine') if c.strip()]
    cuisines = []
    for c in checked + custom:
        if c and c not in cuisines:
            cuisines.append(c)

    # 주소 검색(우편번호+도로명주소)을 새로 했으면 세 값을 합치고,
    # 검색하지 않았으면(=편집 폼에서 주소를 건드리지 않은 경우) 상세주소
    # 칸에 그대로 들어있는 기존 주소 문자열을 유지한다.
    postcode = post.get('postcode', '').strip()
    road_address = post.get('road_address', '').strip()
    detail_address = post.get('detail_address', '').strip()
    if postcode or road_address:
        address = f'[{postcode}] {road_address} {detail_address}'.strip()
    else:
        address = detail_address

    return {
        'name': post.get('name', '').strip(),
        'cuisine_type': ','.join(cuisines),
        'description': post.get('description', '').strip(),
        'phone': post.get('phone', '').strip(),
        'address': address,
        'min_order_amount': min_order_amount,
        'delivery_fee': delivery_fee,
        'is_open': post.get('is_open') == 'on',
    }


def _redirect_to_restaurant(restaurant):
    return redirect(f"{reverse('web:my_restaurant')}?rid={restaurant.id}")


@owner_required
def my_restaurant(request):
    """내 매장 정보 조회 및 수정 요청. 매장이 여러 개면 rid 파라미터로 선택된 매장을 표시한다."""
    restaurants = list(Restaurant.objects.filter(owner=request.user))
    rid = request.POST.get('rid') or request.GET.get('rid')
    restaurant = None
    if rid:
        restaurant = next((r for r in restaurants if str(r.id) == str(rid)), None)
    if restaurant is None:
        restaurant = restaurants[0] if restaurants else None

    pending_edit = None
    if restaurant:
        pending_edit = RestaurantEditRequest.objects.filter(
            restaurant=restaurant, status=RestaurantEditRequest.Status.PENDING,
        ).first()

    if request.method == 'POST':
        if not restaurant:
            messages.error(request, '등록된 매장이 없습니다.')
        elif pending_edit:
            messages.error(request, '이미 처리 대기 중인 수정 요청이 있습니다.')
        else:
            proposed = _parse_form(request.POST)
            if not proposed['name']:
                messages.error(request, '매장명은 필수입니다.')
            elif proposed['min_order_amount'] < 0 or proposed['delivery_fee'] < 0:
                messages.error(request, '최소주문금액/배달비는 0 이상의 숫자여야 합니다.')
            else:
                changes = {
                    field: value for field, value in proposed.items()
                    if value != getattr(restaurant, field)
                }
                if not changes:
                    messages.info(request, '변경된 내용이 없습니다.')
                else:
                    RestaurantEditRequest.objects.create(
                        restaurant=restaurant, requested_by=request.user, changes=changes,
                    )
                    messages.success(request, '매장 정보 수정 요청이 접수되었습니다. 관리자 승인 후 반영됩니다.')
        return _redirect_to_restaurant(restaurant) if restaurant else redirect('web:my_restaurant')

    selected_presets, custom_cuisines = [], []
    if restaurant:
        current = _split_cuisines(restaurant.cuisine_type)
        selected_presets = [c for c in current if c in CUISINE_PRESETS]
        custom_cuisines = [c for c in current if c not in CUISINE_PRESETS]

    return render(request, 'web/my_restaurant.html', {
        'restaurants': restaurants,
        'restaurant': restaurant, 'pending_edit': pending_edit,
        'cuisine_presets': CUISINE_PRESETS,
        'selected_presets': selected_presets,
        'custom_cuisines': custom_cuisines,
    })


@owner_required
def add_restaurant(request):
    """이미 계정이 있는 점주가 매장을 추가로 등록한다. (회원가입 시 최초 매장 등록과는 별개)"""
    if request.method == 'POST':
        proposed = _parse_form(request.POST)
        if not proposed['name']:
            messages.error(request, '매장명은 필수입니다.')
        elif proposed['min_order_amount'] < 0 or proposed['delivery_fee'] < 0:
            messages.error(request, '최소주문금액/배달비는 0 이상의 숫자여야 합니다.')
        else:
            restaurant = Restaurant.objects.create(owner=request.user, **proposed)
            messages.success(request, '매장이 추가되었습니다.')
            return _redirect_to_restaurant(restaurant)
        return render(request, 'web/restaurant_add.html', {
            'cuisine_presets': CUISINE_PRESETS,
            'selected_presets': request.POST.getlist('cuisine_type'),
            'custom_cuisines': [c for c in request.POST.getlist('custom_cuisine') if c.strip()],
            'form': request.POST,
        })

    return render(request, 'web/restaurant_add.html', {
        'cuisine_presets': CUISINE_PRESETS,
        'selected_presets': [],
        'custom_cuisines': [],
        'form': {},
    })
