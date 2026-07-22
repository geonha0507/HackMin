"""점주(Owner) 웹 화면 - 주문/상품/매출/리뷰 관리."""

from datetime import date, timedelta

from django.contrib import messages
from django.db.models import Count, Max, Sum
from django.shortcuts import get_object_or_404, redirect, render

from orders.models import Order
from restaurants.models import Menu, MenuCategory, Restaurant
from restaurants.selectors import owned_restaurant_ids, owned_restaurants
from reviews.models import Review, ReviewReply

from ..decorators import owner_required

# 매출 집계 대상 상태
SALES_STATUSES = (
    Order.Status.PLACED, Order.Status.ACCEPTED, Order.Status.COOKING,
    Order.Status.COOKED, Order.Status.DELIVERING, Order.Status.DELIVERED,
)
ORDER_STATUS_LABELS = dict(Order.Status.choices)
MENU_STATUS_LABELS = dict(Menu.Status.choices)

# 상품 가격 상한선. 비정상적으로 큰 숫자 입력(정수 오버플로우 등)을 막는다.
MAX_MENU_PRICE = 10_000_000

def _owned_restaurants(user):
    return owned_restaurants(user)


def _owned_ids(user):
    return owned_restaurant_ids(user)


@owner_required
def dashboard(request):
    ids = _owned_ids(request.user)
    orders = Order.objects.filter(restaurant_id__in=ids)
    today = date.today()
    today_orders = orders.filter(created_at__date=today)
    sales_qs = orders.filter(status__in=SALES_STATUSES)
    ctx = {
        'restaurants': _owned_restaurants(request.user),
        'pending_count': orders.filter(status=Order.Status.PLACED).count(),
        'cooking_count': orders.filter(
            status__in=(Order.Status.ACCEPTED, Order.Status.COOKING, Order.Status.COOKED)
        ).count(),
        'today_order_count': today_orders.count(),
        'today_sales': today_orders.filter(status__in=SALES_STATUSES).aggregate(s=Sum('total'))['s'] or 0,
        'total_sales': sales_qs.aggregate(s=Sum('total'))['s'] or 0,
        'recent_orders': orders.select_related('restaurant')[:8],
        'menu_count': Menu.objects.filter(restaurant_id__in=ids).count(),
    }
    return render(request, 'web/owner/dashboard.html', ctx)


# ------------------------------------------------------------------ 주문
@owner_required
def order_list(request):
    ids = _owned_ids(request.user)
    orders = Order.objects.filter(restaurant_id__in=ids).select_related('restaurant')
    status = request.GET.get('status', '')
    if status:
        orders = orders.filter(status=status)
    ctx = {
        'orders': orders[:200],
        'status': status,
        'status_choices': Order.Status.choices,
    }
    return render(request, 'web/owner/orders.html', ctx)


@owner_required
def order_detail(request, pk):
    ids = _owned_ids(request.user)
    order = get_object_or_404(
        Order.objects.select_related('restaurant', 'user').prefetch_related('items'),
        pk=pk, restaurant_id__in=ids,
    )
    if request.method == 'POST':
        action = request.POST.get('action')
        if action == 'accept' and order.status == Order.Status.PLACED:
            order.status = Order.Status.ACCEPTED
        elif action == 'reject' and order.status == Order.Status.PLACED:
            order.status = Order.Status.REJECTED
        elif action == 'status':
            new_status = request.POST.get('status')
            if new_status in dict(Order.Status.choices):
                order.status = new_status
        elif action == 'cancel':
            order.status = Order.Status.CANCELLED
        order.save(update_fields=['status', 'updated_at'])
        messages.success(request, '주문 상태를 변경했습니다.')
        return redirect('web:owner_order_detail', pk=pk)
    return render(request, 'web/owner/order_detail.html', {
        'order': order, 'status_choices': Order.Status.choices,
    })


# ------------------------------------------------------------------ 상품
@owner_required
def product_list(request):
    ids = _owned_ids(request.user)
    menus = Menu.objects.filter(restaurant_id__in=ids).select_related('restaurant', 'category')
    return render(request, 'web/owner/products.html', {
        'menus': menus,
        'status_labels': MENU_STATUS_LABELS,
    })


@owner_required
def product_form(request, pk=None):
    restaurants = _owned_restaurants(request.user)
    ids = list(restaurants.values_list('id', flat=True))
    menu = None
    if pk:
        menu = get_object_or_404(Menu, pk=pk, restaurant_id__in=ids)

    if request.method == 'POST':
        restaurant = get_object_or_404(Restaurant, pk=request.POST.get('restaurant'), owner=request.user)
        category_id = request.POST.get('category') or None
        category = None
        if category_id:
            category = MenuCategory.objects.filter(pk=category_id, restaurant=restaurant).first()
        name = request.POST.get('name', '').strip()
        try:
            price = int(request.POST.get('price') or 0)
        except (TypeError, ValueError):
            price = -1
        description = request.POST.get('description', '').strip()
        status = request.POST.get('status', Menu.Status.ON_SALE)
        if not name:
            messages.error(request, '상품명을 입력하세요.')
        elif price < 0 or price > MAX_MENU_PRICE:
            messages.error(request, f'가격은 0원 이상 {MAX_MENU_PRICE:,}원 이하로 입력하세요.')
        else:
            if menu is None:
                menu = Menu(restaurant=restaurant)
            menu.restaurant = restaurant
            menu.category = category
            menu.name = name
            menu.price = price
            menu.description = description
            menu.status = status if status in MENU_STATUS_LABELS else Menu.Status.ON_SALE
            if request.FILES.get('image'):
                menu.image = request.FILES['image']
            menu.save()
            messages.success(request, '상품을 저장했습니다.')
            return redirect('web:owner_products')

    categories = MenuCategory.objects.filter(restaurant_id__in=ids)
    return render(request, 'web/owner/product_form.html', {
        'menu': menu,
        'restaurants': restaurants,
        'categories': categories,
        'status_choices': Menu.Status.choices,
    })


@owner_required
def product_delete(request, pk):
    ids = _owned_ids(request.user)
    menu = get_object_or_404(Menu, pk=pk, restaurant_id__in=ids)
    if request.method == 'POST':
        menu.delete()
        messages.success(request, '상품을 삭제했습니다.')
    return redirect('web:owner_products')


@owner_required
def category_list(request):
    restaurants = _owned_restaurants(request.user)
    ids = list(restaurants.values_list('id', flat=True))
    if request.method == 'POST':
        action = request.POST.get('action')
        if action == 'create':
            restaurant = Restaurant.objects.filter(
                pk=request.POST.get('restaurant'),
                owner=request.user,
            ).first()

            name = request.POST.get('name', '').strip()

            if not restaurant:
                messages.error(request, '유효한 매장을 선택하세요.')

            elif not name:
                messages.error(request, '카테고리 이름을 입력하세요.')

            elif MenuCategory.objects.filter(
                restaurant=restaurant,
                name__iexact=name,
            ).exists():
                messages.error(
                    request,
                    '이미 같은 이름의 카테고리가 있습니다.',
                )

            else:
                last_order = (
                    MenuCategory.objects.filter(restaurant=restaurant)
                    .aggregate(m=Max('display_order'))['m']
                )
                next_order = (last_order or 0) + 1

                MenuCategory.objects.create(
                    restaurant=restaurant,
                    name=name,
                    display_order=next_order,
                )
                messages.success(request, '카테고리를 추가했습니다.')
        elif action == 'delete':
            cat = MenuCategory.objects.filter(pk=request.POST.get('category_id'), restaurant_id__in=ids).first()
            if cat:
                cat.delete()
                messages.success(request, '카테고리를 삭제했습니다.')
        elif action in ('move_up', 'move_down'):
            cat = MenuCategory.objects.filter(pk=request.POST.get('category_id'), restaurant_id__in=ids).first()
            if cat:
                siblings = MenuCategory.objects.filter(restaurant=cat.restaurant).order_by('display_order', 'pk')
                sibling_ids = list(siblings.values_list('pk', flat=True))
                idx = sibling_ids.index(cat.pk)
                swap_idx = idx - 1 if action == 'move_up' else idx + 1
                if 0 <= swap_idx < len(sibling_ids):
                    neighbor = siblings.get(pk=sibling_ids[swap_idx])
                    cat.display_order, neighbor.display_order = neighbor.display_order, cat.display_order
                    cat.save(update_fields=['display_order'])
                    neighbor.save(update_fields=['display_order'])
                    messages.success(request, '순서를 변경했습니다.')
        return redirect('web:owner_categories')
    categories = (
        MenuCategory.objects.filter(restaurant_id__in=ids)
        .select_related('restaurant')
        .order_by('restaurant', 'display_order')
    )
    return render(request, 'web/owner/categories.html', {
        'categories': categories, 'restaurants': restaurants,
    })


# ------------------------------------------------------------------ 매출
@owner_required
def sales(request):
    ids = _owned_ids(request.user)
    today = date.today()
    start = request.GET.get('start') or str(today - timedelta(days=29))
    end = request.GET.get('end') or str(today)

    qs = Order.objects.filter(restaurant_id__in=ids, status__in=SALES_STATUSES,
                              created_at__date__gte=start, created_at__date__lte=end)
    daily = list(
        qs.values('created_at__date')
        .annotate(order_count=Count('id'), amount=Sum('total'))
        .order_by('created_at__date')
    )
    by_menu = list(
        qs.values('items__menu_name')
        .annotate(qty=Sum('items__quantity'), amount=Sum('items__line_total'))
        .order_by('-amount')[:20]
    )
    max_amount = max([d['amount'] or 0 for d in daily], default=0)
    for d in daily:
        d['pct'] = round((d['amount'] or 0) / max_amount * 100) if max_amount else 0
    ctx = {
        'start': start, 'end': end,
        'daily': daily,
        'by_menu': [b for b in by_menu if b['items__menu_name']],
        'total_sales': qs.aggregate(s=Sum('total'))['s'] or 0,
        'order_count': qs.count(),
        'max_amount': max_amount,
        'restaurants': _owned_restaurants(request.user),
    }
    return render(request, 'web/owner/sales.html', ctx)


# ------------------------------------------------------------------ 리뷰
@owner_required
def review_list(request):
    ids = _owned_ids(request.user)
    if request.method == 'POST':
        review = Review.objects.filter(pk=request.POST.get('review_id'), restaurant_id__in=ids).first()
        content = request.POST.get('content', '').strip()
        if review and content:
            ReviewReply.objects.update_or_create(
                review=review, defaults={'owner': request.user, 'content': content},
            )
            messages.success(request, '답변을 등록했습니다.')
        return redirect('web:owner_reviews')
    reviews = (
        Review.objects.filter(restaurant_id__in=ids)
        .select_related('restaurant', 'user')
        .prefetch_related('reply', 'images')
    )
    return render(request, 'web/owner/reviews.html', {'reviews': reviews})