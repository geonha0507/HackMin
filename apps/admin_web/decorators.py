"""admin_web 전용 접근 제어 데코레이터.

apps/web/decorators.py 를 그대로 재사용하지 않는 이유:
그쪽 role_required는 실패 시 '/web/login' 이나 'web:owner_dashboard' 로
리다이렉트하는데, 이 이름/경로들은 admin-web 컨테이너의 URL 설정
(config.urls_admin)에는 존재하지 않아서 NoReverseMatch/404가 난다.
그래서 admin_web 안에서 완결되는 버전을 별도로 둔다.
"""

from functools import wraps

from django.contrib import messages
from django.shortcuts import redirect


def admin_required(view):
    @wraps(view)
    def wrapper(request, *args, **kwargs):
        user = request.user

        if not user.is_authenticated:
            return redirect(f'/admin-web/login?next={request.path}')

        if user.role != 'admin':
            messages.error(request, '접근 권한이 없습니다.')
            return redirect('admin_web:login')

        return view(request, *args, **kwargs)

    return wrapper
