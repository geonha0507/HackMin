"""역할 기반 접근 제어 데코레이터 (세션 인증 기반)."""

from functools import wraps

from django.contrib import messages
from django.shortcuts import redirect


def _redirect_login(request):
    return redirect(f"/web/login?next={request.path}")


def role_required(*roles):
    """로그인 + 지정한 role 중 하나여야 접근 가능."""

    def decorator(view):
        @wraps(view)
        def wrapper(request, *args, **kwargs):
            user = request.user
            if not user.is_authenticated:
                return _redirect_login(request)
            if roles and user.role not in roles:
                messages.error(request, '접근 권한이 없습니다.')
                # 역할에 맞는 홈으로 보냄
                if user.role == 'admin':
                    return redirect('web:admin_dashboard')
                if user.role == 'owner':
                    return redirect('web:owner_dashboard')
                return _redirect_login(request)
            return view(request, *args, **kwargs)

        return wrapper

    return decorator


owner_required = role_required('owner')
admin_required = role_required('admin')
