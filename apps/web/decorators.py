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
                # 관리자 화면은 admin_web 앱(별도 컨테이너)으로 이동했으므로
                # 여기(owner-web 컨테이너)에는 admin_dashboard 라우트가 없다.
                if user.role == 'owner':
                    return redirect('web:owner_dashboard')
                return _redirect_login(request)
            return view(request, *args, **kwargs)

        return wrapper

    return decorator


owner_required = role_required('owner')
admin_required = role_required('admin')
