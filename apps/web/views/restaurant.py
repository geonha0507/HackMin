"""마이페이지 > 내 매장 관리.

매장 정보 수정은 즉시 반영되지 않고 RestaurantEditRequest 로 접수되어
관리자 승인 후에만 실제 Restaurant 레코드에 반영된다.
"""

import os
from enrollment.models import EnrollmentRequest
from django.contrib.auth import get_user_model

User = get_user_model()

from datetime import date

from django.contrib import messages
from django.shortcuts import get_object_or_404, redirect, render
from django.urls import reverse

from restaurants.models import (
    Restaurant,
    RestaurantClosedDate,
    RestaurantEditRequest,
    RestaurantNotice,
    RestaurantRegularClosedDay,
)
from ..decorators import owner_required

EDITABLE_FIELDS = [
    'name', 'cuisine_type', 'description', 'phone', 'address',
    'min_order_amount', 'delivery_fee', 'is_open',
]

CUISINE_PRESETS = ['한식', '중식', '양식', '일식', '분식', '치킨', '피자', '햄버거', '카페/디저트', '아시안']

# 최소주문금액/배달비 상한선. 비정상적으로 큰 숫자 입력(정수 오버플로우 등)을 막는다.
MAX_MIN_ORDER_AMOUNT = 1_000_000
MAX_DELIVERY_FEE = 100_000

_ALLOWED_IMAGE_EXTENSIONS = {'.jpg', '.jpeg', '.png', '.gif', '.webp'}
_MAX_IMAGE_SIZE = 5 * 1024 * 1024  # 5MB

_ALLOWED_LICENSE_EXTENSIONS = {'.pdf', '.jpg', '.jpeg', '.png'}
_MAX_LICENSE_SIZE = 10 * 1024 * 1024  # 10MB

def _is_reviewable_restaurant(restaurant):
    """이 매장이 정상 운영 가능(승인 완료) 상태인지 확인한다.

    add_restaurant로 만든 매장은 EnrollmentRequest.status로 판단하고,
    signup_view의 최초 매장은 아직 EnrollmentRequest가 없을 수 있어
    owner.status로 판단한다.
    """
    enrollment = getattr(restaurant, 'enrollment_request', None)
    if enrollment:
        return enrollment.status == 'approved'
    return restaurant.owner.status == User.Status.ACTIVE

def _valid_image(upload):
    extension = os.path.splitext(upload.name)[1].lower()
    return extension in _ALLOWED_IMAGE_EXTENSIONS and upload.size <= _MAX_IMAGE_SIZE


def _valid_license(upload):
    extension = os.path.splitext(upload.name)[1].lower()
    return extension in _ALLOWED_LICENSE_EXTENSIONS and upload.size <= _MAX_LICENSE_SIZE


def _split_cuisines(cuisine_type):
    return [c.strip() for c in cuisine_type.split(',') if c.strip()]


def _parse_form(post):
    try:
        min_order_amount = int(post.get('min_order_amount', '0') or 0)
        delivery_fee = int(post.get('delivery_fee', '0') or 0)
    except (TypeError, ValueError):
        min_order_amount = delivery_fee = -1
    else:
        if not (0 <= min_order_amount <= MAX_MIN_ORDER_AMOUNT):
            min_order_amount = -1
        if not (0 <= delivery_fee <= MAX_DELIVERY_FEE):
            delivery_fee = -1

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
        elif not _is_reviewable_restaurant(restaurant):
            messages.error(request, '심사 대기 또는 반려 중인 매장은 정보를 수정할 수 없습니다.')
        elif pending_edit:
            messages.error(request, '이미 처리 대기 중인 수정 요청이 있습니다.')
        else:
            proposed = _parse_form(request.POST)
            if not proposed['name']:
                messages.error(request, '매장명은 필수입니다.')
            elif proposed['min_order_amount'] < 0 or proposed['delivery_fee'] < 0:
                messages.error(
                    request,
                    f'최소주문금액은 0~{MAX_MIN_ORDER_AMOUNT:,}원, '
                    f'배달비는 0~{MAX_DELIVERY_FEE:,}원 사이여야 합니다.',
                )
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

    closed_dates = (
        restaurant.closed_dates.filter(date__gte=date.today()) if restaurant else []
    )
    regular_closed_weekdays = (
        set(restaurant.regular_closed_days.values_list('weekday', flat=True)) if restaurant else set()
    )
    notices = restaurant.notices.all() if restaurant else []

    enrollment = getattr(restaurant, 'enrollment_request', None) if restaurant else None
    restaurant_pending_review = bool(enrollment and enrollment.status == 'pending')
    restaurant_rejected = bool(enrollment and enrollment.status == 'rejected')
    restaurant_rejection_reason = enrollment.rejection_reason if restaurant_rejected else ''
    restaurant_reviewable = _is_reviewable_restaurant(restaurant) if restaurant else True

    return render(request, 'web/my_restaurant.html', {
        'restaurants': restaurants,
        'restaurant': restaurant, 'pending_edit': pending_edit,
        'cuisine_presets': CUISINE_PRESETS,
        'selected_presets': selected_presets,
        'custom_cuisines': custom_cuisines,
        'closed_dates': closed_dates,
        'weekday_choices': RestaurantRegularClosedDay.Weekday.choices,
        'regular_closed_weekdays': regular_closed_weekdays,
        'notices': notices,
        'restaurant_pending_review': restaurant_pending_review,
        'restaurant_rejected': restaurant_rejected,
        'restaurant_rejection_reason': restaurant_rejection_reason,
        'restaurant_reviewable': restaurant_reviewable,
    })


@owner_required
def add_restaurant(request):
    """이미 계정이 있는 점주가 매장을 추가로 등록한다. 관리자 승인 후 노출된다."""
    if request.method == 'POST':
        proposed = _parse_form(request.POST)
        proposed['is_open'] = False  # 승인 전에는 강제로 비영업 처리
        image = request.FILES.get('image')
        business_license = request.FILES.get('business_license')

        if not proposed['name']:
            messages.error(request, '매장명은 필수입니다.')
        elif proposed['min_order_amount'] < 0 or proposed['delivery_fee'] < 0:
            messages.error(request, '최소주문금액/배달비는 0 이상의 숫자여야 합니다.')
        elif not business_license:
            messages.error(request, '사업자등록증 파일을 첨부하세요.')
        elif not _valid_license(business_license):
            messages.error(request, '사업자등록증은 PDF, JPG, JPEG, PNG · 최대 10MB만 가능합니다.')
        elif image and not _valid_image(image):
            messages.error(request, '이미지는 jpg/png/gif/webp, 5MB 이하만 가능합니다.')
        else:
            restaurant = Restaurant.objects.create(
                owner=request.user, business_license=business_license, **proposed,
            )
            if image:
                restaurant.image = image
                restaurant.save(update_fields=['image'])

            EnrollmentRequest.objects.create(
                restaurant=restaurant,
                username=request.user.username,
                phone=(request.user.phone or '')[:20],
                owner_name=request.user.nickname or request.user.username,
                restaurant_name=restaurant.name,
                status='pending',
            )

            messages.success(request, '매장이 추가되었습니다. 관리자 승인 후 노출됩니다.')
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


@owner_required
def closed_date_add(request):
    """특정 휴무일 등록. 해당 날짜에는 앱에서 주문을 받지 않는다."""
    if request.method != 'POST':
        return redirect('web:my_restaurant')

    restaurant = get_object_or_404(Restaurant, pk=request.POST.get('rid'), owner=request.user)
    if not _is_reviewable_restaurant(restaurant):
        messages.error(request, '심사 대기 또는 반려 중인 매장은 휴무일을 등록할 수 없습니다.')
        return _redirect_to_restaurant(restaurant)

    raw_date = request.POST.get('date', '').strip()
    try:
        closed_on = date.fromisoformat(raw_date)
    except ValueError:
        messages.error(request, '올바른 날짜를 선택하세요.')
    else:
        _, created = RestaurantClosedDate.objects.get_or_create(restaurant=restaurant, date=closed_on)
        if created:
            messages.success(request, '휴무일을 등록했습니다.')
        else:
            messages.info(request, '이미 등록된 휴무일입니다.')
    return _redirect_to_restaurant(restaurant)

@owner_required
def closed_date_delete(request, pk):
    """특정 휴무일 삭제."""
    closed_date = get_object_or_404(RestaurantClosedDate, pk=pk, restaurant__owner=request.user)
    restaurant = closed_date.restaurant
    if request.method == 'POST':
        closed_date.delete()
        messages.success(request, '휴무일을 삭제했습니다.')
    return _redirect_to_restaurant(restaurant)


@owner_required
def regular_closed_days_update(request):
    """정기휴무 요일 저장 (선택된 요일 전체로 교체)."""
    if request.method != 'POST':
        return redirect('web:my_restaurant')

    restaurant = get_object_or_404(Restaurant, pk=request.POST.get('rid'), owner=request.user)
    if not _is_reviewable_restaurant(restaurant):
        messages.error(request, '심사 대기 또는 반려 중인 매장은 정기휴무일을 설정할 수 없습니다.')
        return _redirect_to_restaurant(restaurant)

    valid_weekdays = {str(val) for val, _ in RestaurantRegularClosedDay.Weekday.choices}
    selected = sorted({
        int(w) for w in request.POST.getlist('weekday') if w in valid_weekdays
    })

    RestaurantRegularClosedDay.objects.filter(restaurant=restaurant).delete()
    RestaurantRegularClosedDay.objects.bulk_create([
        RestaurantRegularClosedDay(restaurant=restaurant, weekday=w) for w in selected
    ])
    messages.success(request, '정기휴무일을 저장했습니다.')
    return _redirect_to_restaurant(restaurant)


@owner_required
def restaurant_image_upload(request):
    """매장 대표 이미지 업로드/교체. 승인된 매장만 즉시 반영된다."""
    if request.method != 'POST':
        return redirect('web:my_restaurant')

    restaurant = get_object_or_404(Restaurant, pk=request.POST.get('rid'), owner=request.user)
    if not _is_reviewable_restaurant(restaurant):
        messages.error(request, '심사 대기 또는 반려 중인 매장은 사진을 변경할 수 없습니다.')
        return _redirect_to_restaurant(restaurant)

    image = request.FILES.get('image')
    if not image:
        messages.error(request, '이미지 파일을 선택하세요.')
    elif not _valid_image(image):
        messages.error(request, '이미지는 jpg/png/gif/webp, 5MB 이하만 가능합니다.')
    else:
        restaurant.image = image
        restaurant.save(update_fields=['image'])
        messages.success(request, '매장 사진을 변경했습니다.')
    return _redirect_to_restaurant(restaurant)


@owner_required
def notice_add(request):
    """매장 공지사항 등록. 고객 앱에 즉시 노출된다."""
    if request.method != 'POST':
        return redirect('web:my_restaurant')

    restaurant = get_object_or_404(Restaurant, pk=request.POST.get('rid'), owner=request.user)
    if not _is_reviewable_restaurant(restaurant):
        messages.error(request, '심사 대기 또는 반려 중인 매장은 공지사항을 등록할 수 없습니다.')
        return _redirect_to_restaurant(restaurant)

    title = request.POST.get('title', '').strip()
    content = request.POST.get('content', '').strip()

    if not title:
        messages.error(request, '제목을 입력하세요.')
    elif not content:
        messages.error(request, '내용을 입력하세요.')
    else:
        RestaurantNotice.objects.create(restaurant=restaurant, title=title, content=content)
        messages.success(request, '공지사항을 등록했습니다.')
    return _redirect_to_restaurant(restaurant)

@owner_required
def notice_delete(request, pk):
    """매장 공지사항 삭제."""
    notice = get_object_or_404(RestaurantNotice, pk=pk, restaurant__owner=request.user)
    restaurant = notice.restaurant
    if request.method == 'POST':
        notice.delete()
        messages.success(request, '공지사항을 삭제했습니다.')
    return _redirect_to_restaurant(restaurant)
