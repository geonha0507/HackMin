"""Role-based DRF permissions.

Roles: customer, owner, admin, rider (see accounts.User.Role).
"""

from rest_framework.permissions import BasePermission, IsAuthenticated


class RolePermission(BasePermission):
    required_role = None

    def has_permission(self, request, view):
        user = request.user
        if not (user and user.is_authenticated):
            return False
        if self.required_role is None:
            return True
        return getattr(user, 'role', None) == self.required_role


class IsCustomer(RolePermission):
    required_role = 'customer'


class IsOwner(RolePermission):
    required_role = 'owner'


class IsRider(RolePermission):
    required_role = 'rider'


class IsAdminRole(BasePermission):
    def has_permission(self, request, view):
        user = request.user
        return bool(
            user
            and user.is_authenticated
            and (getattr(user, 'role', None) == 'admin' or user.is_staff)
        )


__all__ = [
    'IsAuthenticated',
    'IsCustomer',
    'IsOwner',
    'IsRider',
    'IsAdminRole',
]
