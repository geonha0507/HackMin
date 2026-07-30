"""Owner product / category / option management."""

import os

from django.db import transaction
from rest_framework.decorators import api_view, parser_classes, permission_classes
from rest_framework.parsers import FormParser, MultiPartParser
from rest_framework.response import Response

from common.exceptions import error_response
from common.permissions import IsOwner
from restaurants.models import Menu, MenuCategory, MenuOption, MenuOptionGroup, Restaurant
from .serializers import (
    CategorySerializer,
    OptionSerializer,
    ProductSerializer,
)

_ALLOWED_IMAGE_EXT = {'.jpg', '.jpeg', '.png', '.gif', '.webp'}


def owned_restaurant_ids(user):
    return list(Restaurant.objects.filter(owner=user).values_list('id', flat=True))


def _menu_scope(request):
    """본인 매장 메뉴만 조회."""
    return Menu.objects.filter(restaurant__owner=request.user)


def _assert_owns_restaurant(request, restaurant_id):
    return Restaurant.objects.filter(id=restaurant_id, owner=request.user).exists()


def _assert_reviewable(restaurant):
    """심사 대기·반려 매장에는 상품/카테고리를 등록할 수 없다.

    예전에는 apps/web 화면에서만 검사해서 API 를 직접 호출하면 우회가 가능했다.
    """
    from restaurants.selectors import is_reviewable_restaurant
    if is_reviewable_restaurant(restaurant):
        return None
    return error_response(
        'restaurant_not_approved', '심사 대기 또는 반려 중인 매장은 등록할 수 없습니다.', 403,
    )


# --- Products --------------------------------------------------------------
@api_view(['GET', 'POST'])
@permission_classes([IsOwner])
def product_list_create(request):
    if request.method == 'GET':
        products = _menu_scope(request).order_by('-created_at')
        return Response({'results': ProductSerializer(products, many=True).data})

    # 상품 등록
    serializer = ProductSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    restaurant = serializer.validated_data.get('restaurant')
    if restaurant is None:
        first = Restaurant.objects.filter(owner=request.user).first()
        if not first:
            return error_response('no_restaurant', '등록된 매장이 없습니다.', 400)
        restaurant = first
    elif not _assert_owns_restaurant(request, restaurant.id):
        return error_response('forbidden', '본인 매장에만 상품을 등록할 수 있습니다.', 403)
    err = _assert_reviewable(restaurant)
    if err:
        return err
    product = serializer.save(restaurant=restaurant)
    return Response(ProductSerializer(product).data, status=201)


@api_view(['GET', 'PUT', 'DELETE'])
@permission_classes([IsOwner])
def product_detail(request, pk):
    """상품 조회/수정/삭제."""
    product = _menu_scope(request).filter(pk=pk).first()
    if not product:
        return error_response('not_found', '상품을 찾을 수 없습니다.', 404)

    if request.method == 'DELETE':
        product.delete()
        return Response(status=204)

    if request.method == 'GET':
        return Response(ProductSerializer(product).data)

    serializer = ProductSerializer(product, data=request.data, partial=True)
    serializer.is_valid(raise_exception=True)
    serializer.save()
    return Response(serializer.data)


@api_view(['POST'])
@permission_classes([IsOwner])
@parser_classes([MultiPartParser, FormParser])
def product_image(request, pk):
    """상품 이미지 업로드. 허용된 확장자만 업로드 가능."""
    product = _menu_scope(request).filter(pk=pk).first()
    if not product:
        return error_response('not_found', '상품을 찾을 수 없습니다.', 404)
    upload = request.FILES.get('image')
    if not upload:
        return error_response('bad_request', 'image 파일이 필요합니다.', 400)
    ext = os.path.splitext(upload.name)[1].lower()
    if ext not in _ALLOWED_IMAGE_EXT:
        return error_response('invalid_file_type', '허용되지 않는 파일 형식입니다.', 400)
    product.image = upload
    product.save(update_fields=['image'])
    return Response({'image': product.image.url}, status=201)


@api_view(['PUT'])
@permission_classes([IsOwner])
def product_status(request, pk):
    product = _menu_scope(request).filter(pk=pk).first()
    if not product:
        return error_response('not_found', '상품을 찾을 수 없습니다.', 404)
    new_status = request.data.get('status')
    if new_status not in Menu.Status.values:
        return error_response('bad_request', '유효하지 않은 상태입니다.', 400)
    product.status = new_status
    product.save(update_fields=['status'])
    return Response(ProductSerializer(product).data)


# --- Categories ------------------------------------------------------------
@api_view(['GET', 'POST'])
@permission_classes([IsOwner])
def category_list_create(request):
    if request.method == 'GET':
        cats = MenuCategory.objects.filter(restaurant__owner=request.user)
        return Response({'results': CategorySerializer(cats.order_by('display_order'), many=True).data})

    serializer = CategorySerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    restaurant = serializer.validated_data['restaurant']
    if not _assert_owns_restaurant(request, restaurant.id):
        return error_response('forbidden', '본인 매장에만 카테고리를 만들 수 있습니다.', 403)
    err = _assert_reviewable(restaurant)
    if err:
        return err
    category = serializer.save()
    return Response(CategorySerializer(category).data, status=201)


@api_view(['PUT', 'DELETE'])
@permission_classes([IsOwner])
def category_detail(request, pk):
    category = MenuCategory.objects.filter(pk=pk, restaurant__owner=request.user).first()
    if not category:
        return error_response('not_found', '카테고리를 찾을 수 없습니다.', 404)
    if request.method == 'DELETE':
        category.delete()
        return Response(status=204)
    serializer = CategorySerializer(category, data=request.data, partial=True)
    serializer.is_valid(raise_exception=True)
    serializer.save()
    return Response(serializer.data)


@api_view(['POST'])
@permission_classes([IsOwner])
def category_move(request, pk):
    """카테고리 표시 순서를 한 칸 위/아래로 옮긴다. body: {"direction": "up"|"down"}

    웹에서 display_order 를 두 번 PUT 하는 방식은 중간에 실패하면 순서가
    깨진다. 이웃과의 값 교환을 한 트랜잭션으로 묶어 서버에서 처리한다.
    """
    direction = request.data.get('direction')
    if direction not in ('up', 'down'):
        return error_response('bad_request', "direction 은 'up' 또는 'down' 이어야 합니다.", 400)

    category = MenuCategory.objects.filter(pk=pk, restaurant__owner=request.user).first()
    if not category:
        return error_response('not_found', '카테고리를 찾을 수 없습니다.', 404)

    with transaction.atomic():
        siblings = list(
            MenuCategory.objects.select_for_update()
            .filter(restaurant=category.restaurant)
            .order_by('display_order', 'pk')
        )
        ids = [c.pk for c in siblings]
        idx = ids.index(category.pk)
        swap_idx = idx - 1 if direction == 'up' else idx + 1
        if not (0 <= swap_idx < len(siblings)):
            return error_response('out_of_range', '더 이동할 수 없습니다.', 409)

        neighbor = siblings[swap_idx]
        current = siblings[idx]
        current.display_order, neighbor.display_order = (
            neighbor.display_order, current.display_order,
        )
        current.save(update_fields=['display_order'])
        neighbor.save(update_fields=['display_order'])

    return Response(CategorySerializer(current).data)


# --- Menu options ----------------------------------------------------------
@api_view(['GET', 'POST'])
@permission_classes([IsOwner])
def product_options(request, pk):
    """메뉴 옵션 목록/생성. 옵션은 옵션그룹 하위에 생성한다."""
    product = _menu_scope(request).filter(pk=pk).first()
    if not product:
        return error_response('not_found', '상품을 찾을 수 없습니다.', 404)

    if request.method == 'GET':
        options = MenuOption.objects.filter(group__menu=product)
        return Response({'results': OptionSerializer(options, many=True).data})

    group_id = request.data.get('group')
    if group_id:
        group = MenuOptionGroup.objects.filter(pk=group_id, menu=product).first()
        if not group:
            return error_response('bad_request', '유효하지 않은 옵션 그룹입니다.', 400)
    else:
        group = MenuOptionGroup.objects.create(
            menu=product, name=request.data.get('group_name', '옵션'),
        )
    option = MenuOption.objects.create(
        group=group,
        name=request.data.get('name', ''),
        extra_price=int(request.data.get('extra_price', 0)),
    )
    return Response(OptionSerializer(option).data, status=201)


@api_view(['PUT', 'DELETE'])
@permission_classes([IsOwner])
def option_detail(request, pk):
    option = MenuOption.objects.filter(pk=pk, group__menu__restaurant__owner=request.user).first()
    if not option:
        return error_response('not_found', '옵션을 찾을 수 없습니다.', 404)
    if request.method == 'DELETE':
        option.delete()
        return Response(status=204)
    if 'name' in request.data:
        option.name = request.data['name']
    if 'extra_price' in request.data:
        option.extra_price = int(request.data['extra_price'])
    option.save()
    return Response(OptionSerializer(option).data)
