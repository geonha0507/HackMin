"""Cart pricing helpers shared by cart and order creation."""

from restaurants.models import MenuOption


def option_price_map(option_ids):
    """Return {option_id: extra_price} for the given option ids."""
    if not option_ids:
        return {}
    rows = MenuOption.objects.filter(id__in=option_ids).values_list('id', 'extra_price')
    return {oid: price for oid, price in rows}


def compute_unit_price(menu, option_ids):
    """Server-side unit price = menu price + selected option extras."""
    extras = option_price_map(option_ids)
    return menu.price + sum(extras.get(int(oid), 0) for oid in option_ids)


def compute_line_total(menu, option_ids, quantity):
    return compute_unit_price(menu, option_ids) * max(int(quantity), 0)


def cart_totals(cart):
    """Aggregate subtotal / delivery fee / discount / total for a cart."""
    items = cart.items.select_related('menu', 'menu__restaurant')
    subtotal = 0
    for item in items:
        subtotal += compute_line_total(item.menu, item.options, item.quantity)

    delivery_fee = cart.restaurant.delivery_fee if cart.restaurant else 0
    discount = 0
    if cart.coupon:
        discount = cart.coupon.compute_discount(subtotal)

    total = max(subtotal + delivery_fee - discount, 0)
    return {
        'subtotal': subtotal,
        'delivery_fee': delivery_fee,
        'discount': discount,
        'total': total,
    }
