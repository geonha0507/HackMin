"""점주의 '내 매장 관리' API (/api/v1/owner/restaurants...).

이 흐름은 그동안 apps/web/views/restaurant.py 화면에만 존재했다. DB에 붙지
않는 web_bff 로 화면을 옮기면서 API 로 승격한다.

핵심 규칙 두 가지는 서버에 둔다.
  1) 매장 정보 수정은 즉시 반영되지 않는다. RestaurantEditRequest 로 접수되어
     관리자 승인 후에만 실제 Restaurant 레코드에 반영된다.
  2) 심사 대기·반려 중인 매장은 어떤 항목도 변경할 수 없다.
"""

import os
from datetime import date

from django.db import transaction
from rest_framework.decorators import api_view, parser_classes, permission_classes
from rest_framework.parsers import FormParser, MultiPartParser
from rest_framework.response import Response

from common.exceptions import error_response
from common.permissions import IsOwner
from enrollment.models import EnrollmentRequest
from restaurants.models import (
    Restaurant,
    RestaurantClosedDate,
    RestaurantEditRequest,
    RestaurantNotice,
    RestaurantRegularClosedDay,
)
from restaurants.selectors import is_reviewable_restaurant

from .serializers import (
    ClosedDateSerializer,
    EditRequestSerializer,
    OwnerRestaurantSerializer,
    RestaurantNoticeSerializer,
)

# 매장 정보 수정 요청으로 보낼 수 있는 필드.
EDITABLE_FIELDS = [
    'name', 'cuisine_type', 'description', 'phone', 'address',
    'min_order_amount', 'delivery_fee', 'is_open',
]

# 비정상적으로 큰 숫자 입력(정수 오버플로우 등)을 막는다.
MAX_MIN_ORDER_AMOUNT = 1_000_000
MAX_DELIVERY_FEE = 100_000

_ALLOWED_IMAGE_EXT = {'.jpg', '.jpeg', '.png', '.gif', '.webp'}
_MAX_IMAGE_BYTES = 5 * 1024 * 1024

_ALLOWED_LICENSE_EXT = {'.pdf', '.jpg', '.jpeg', '.png'}
_MAX_LICENSE_BYTES = 10 * 1024 * 1024


# --------------------------------------------------------------------- 헬퍼
def _get_owned(request, pk):
    return Restaurant.objects.filter(pk=pk, owner=request.user).first()


def _guard(request, pk):
    """소유·승인 상태를 함께 확인한다. (매장, 오류응답) 튜플을 돌려준다."""
    restaurant = _get_owned(request, pk)
    if not restaurant:
        return None, error_response('not_found', '매장을 찾을 수 없습니다.', 404)
    if not is_reviewable_restaurant(restaurant):
        return None, error_response(
            'restaurant_not_approved',
            '심사 대기 또는 반려 중인 매장은 변경할 수 없습니다.', 403,
        )
    return restaurant, None


def _validate_upload(upload, allowed_ext, max_bytes, label):
    ext = os.path.splitext(upload.name)[1].lower()
    if ext not in allowed_ext:
        return error_response(
            'invalid_file_type',
            f'{label}은(는) {", ".join(sorted(e[1:] for e in allowed_ext))} 형식만 가능합니다.', 400,
        )
    if upload.size > max_bytes:
        return error_response(
            'file_too_large', f'{label} 크기는 {max_bytes // (1024 * 1024)}MB 이하만 허용됩니다.', 400,
        )
    return None


def _parse_store_fields(data):
    """폼/JSON 양쪽에서 매장 필드를 뽑는다. (값, 오류메시지) 를 돌려준다."""
    def _int(key, upper):
        raw = data.get(key, 0) or 0
        try:
            value = int(raw)
        except (TypeError, ValueError):
            return None
        return value if 0 <= value <= upper else None

    min_order = _int('min_order_amount', MAX_MIN_ORDER_AMOUNT)
    delivery_fee = _int('delivery_fee', MAX_DELIVERY_FEE)

    name = (data.get('name') or '').strip()
    if not name:
        return None, '매장명은 필수입니다.'
    if min_order is None or delivery_fee is None:
        return None, (
            f'최소주문금액은 0~{MAX_MIN_ORDER_AMOUNT:,}원, '
            f'배달비는 0~{MAX_DELIVERY_FEE:,}원 사이여야 합니다.'
        )

    is_open = data.get('is_open')
    return {
        'name': name,
        'cuisine_type': (data.get('cuisine_type') or '').strip(),
        'description': (data.get('description') or '').strip(),
        'phone': (data.get('phone') or '').strip(),
        'address': (data.get('address') or '').strip(),
        'min_order_amount': min_order,
        'delivery_fee': delivery_fee,
        'is_open': str(is_open).lower() in ('1', 'true', 'on', 'yes'),
    }, None


# --------------------------------------------------------------------- 상세
@api_view(['GET'])
@permission_classes([IsOwner])
def owner_restaurant_detail(request, pk):
    """매장 상세 + 휴무일·정기휴무·공지·수정요청·심사 상태를 한 번에 돌려준다.

    화면 하나를 그리는 데 필요한 정보를 묶어 왕복 횟수를 줄인다.
    """
    restaurant = _get_owned(request, pk)
    if not restaurant:
        return error_response('not_found', '매장을 찾을 수 없습니다.', 404)

    pending_edit = RestaurantEditRequest.objects.filter(
        restaurant=restaurant, status=RestaurantEditRequest.Status.PENDING,
    ).first()
    enrollment = getattr(restaurant, 'enrollment_request', None)

    return Response({
        'restaurant': OwnerRestaurantSerializer(restaurant).data,
        'closed_dates': ClosedDateSerializer(
            restaurant.closed_dates.filter(date__gte=date.today()), many=True,
        ).data,
        'regular_closed_days': list(
            restaurant.regular_closed_days.values_list('weekday', flat=True)
        ),
        'notices': RestaurantNoticeSerializer(restaurant.notices.all(), many=True).data,
        'pending_edit': EditRequestSerializer(pending_edit).data if pending_edit else None,
        'enrollment_status': enrollment.status if enrollment else '',
        'rejection_reason': getattr(enrollment, 'rejection_reason', '') if enrollment else '',
    })


# --------------------------------------------------------------------- 매장 추가
def create_restaurant(request):
    """매장 추가. 사업자등록증 필수이며 관리자 승인 전까지 비영업 상태로 둔다.

    라우팅은 restaurants.py 의 owner_restaurant_list_create 가 담당한다.
    (GET 목록과 POST 생성이 같은 경로 owner/restaurants 를 쓰기 때문에
    @api_view 를 두 번 붙일 수 없다.)
    """
    fields, error = _parse_store_fields(request.data)
    if error:
        return error_response('bad_request', error, 400)

    license_file = request.FILES.get('business_license')
    if not license_file:
        return error_response('bad_request', '사업자등록증 파일을 첨부하세요.', 400)
    err = _validate_upload(license_file, _ALLOWED_LICENSE_EXT, _MAX_LICENSE_BYTES, '사업자등록증')
    if err:
        return err

    image = request.FILES.get('image')
    if image:
        err = _validate_upload(image, _ALLOWED_IMAGE_EXT, _MAX_IMAGE_BYTES, '이미지')
        if err:
            return err

    # 승인 전에는 노출되면 안 되므로 영업 상태를 강제로 끈다.
    fields['is_open'] = False

    with transaction.atomic():
        restaurant = Restaurant.objects.create(
            owner=request.user, business_license=license_file, **fields,
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

    return Response(OwnerRestaurantSerializer(restaurant).data, status=201)


# --------------------------------------------------------------------- 수정 요청
@api_view(['POST'])
@permission_classes([IsOwner])
def owner_restaurant_edit_request(request, pk):
    """매장 정보 수정 요청 접수. 즉시 반영되지 않는다."""
    restaurant, err = _guard(request, pk)
    if err:
        return err

    if RestaurantEditRequest.objects.filter(
        restaurant=restaurant, status=RestaurantEditRequest.Status.PENDING,
    ).exists():
        return error_response('already_pending', '이미 처리 대기 중인 수정 요청이 있습니다.', 409)

    proposed, error = _parse_store_fields(request.data)
    if error:
        return error_response('bad_request', error, 400)

    changes = {
        field: value for field, value in proposed.items()
        if field in EDITABLE_FIELDS and value != getattr(restaurant, field)
    }
    if not changes:
        return error_response('no_changes', '변경된 내용이 없습니다.', 400)

    edit = RestaurantEditRequest.objects.create(
        restaurant=restaurant, requested_by=request.user, changes=changes,
    )
    return Response(EditRequestSerializer(edit).data, status=201)


# --------------------------------------------------------------------- 휴무일
@api_view(['POST'])
@permission_classes([IsOwner])
def owner_closed_date_create(request, pk):
    restaurant, err = _guard(request, pk)
    if err:
        return err

    try:
        closed_on = date.fromisoformat((request.data.get('date') or '').strip())
    except ValueError:
        return error_response('bad_request', '올바른 날짜를 선택하세요.', 400)

    obj, created = RestaurantClosedDate.objects.get_or_create(
        restaurant=restaurant, date=closed_on,
    )
    if not created:
        return error_response('already_exists', '이미 등록된 휴무일입니다.', 409)
    return Response(ClosedDateSerializer(obj).data, status=201)


@api_view(['DELETE'])
@permission_classes([IsOwner])
def owner_closed_date_delete(request, pk):
    obj = RestaurantClosedDate.objects.filter(pk=pk, restaurant__owner=request.user).first()
    if not obj:
        return error_response('not_found', '휴무일을 찾을 수 없습니다.', 404)
    obj.delete()
    return Response(status=204)


@api_view(['PUT'])
@permission_classes([IsOwner])
def owner_regular_closed_days(request, pk):
    """정기휴무 요일을 통째로 교체한다. body: {"weekdays": [0, 3]}"""
    restaurant, err = _guard(request, pk)
    if err:
        return err

    valid = {v for v, _ in RestaurantRegularClosedDay.Weekday.choices}
    raw = request.data.get('weekdays') or []
    if isinstance(raw, str):          # 폼 전송 대비
        raw = [raw]
    try:
        selected = sorted({int(w) for w in raw if int(w) in valid})
    except (TypeError, ValueError):
        return error_response('bad_request', '요일 값이 올바르지 않습니다.', 400)

    with transaction.atomic():
        RestaurantRegularClosedDay.objects.filter(restaurant=restaurant).delete()
        RestaurantRegularClosedDay.objects.bulk_create([
            RestaurantRegularClosedDay(restaurant=restaurant, weekday=w) for w in selected
        ])
    return Response({'weekdays': selected})


# --------------------------------------------------------------------- 대표 이미지
@api_view(['POST'])
@permission_classes([IsOwner])
@parser_classes([MultiPartParser, FormParser])
def owner_restaurant_image(request, pk):
    restaurant, err = _guard(request, pk)
    if err:
        return err

    image = request.FILES.get('image')
    if not image:
        return error_response('bad_request', '이미지 파일을 선택하세요.', 400)
    err = _validate_upload(image, _ALLOWED_IMAGE_EXT, _MAX_IMAGE_BYTES, '이미지')
    if err:
        return err

    restaurant.image = image
    restaurant.save(update_fields=['image'])
    return Response({'image': restaurant.image.url}, status=201)


# --------------------------------------------------------------------- 매장 공지
@api_view(['POST'])
@permission_classes([IsOwner])
def owner_notice_create(request, pk):
    restaurant, err = _guard(request, pk)
    if err:
        return err

    title = (request.data.get('title') or '').strip()
    content = (request.data.get('content') or '').strip()
    if not title:
        return error_response('bad_request', '제목을 입력하세요.', 400)
    if not content:
        return error_response('bad_request', '내용을 입력하세요.', 400)

    notice = RestaurantNotice.objects.create(
        restaurant=restaurant, title=title, content=content,
    )
    return Response(RestaurantNoticeSerializer(notice).data, status=201)


@api_view(['DELETE'])
@permission_classes([IsOwner])
def owner_notice_delete(request, pk):
    notice = RestaurantNotice.objects.filter(pk=pk, restaurant__owner=request.user).first()
    if not notice:
        return error_response('not_found', '공지사항을 찾을 수 없습니다.', 404)
    notice.delete()
    return Response(status=204)
