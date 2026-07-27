"""세션 기반 인증 (django.contrib.auth 대체).

기존 apps/web 은 `authenticate()` / `auth_login()` 으로 DB의 사용자 테이블을
직접 읽었다. web_bff 는 DB에 붙지 않으므로 다음과 같이 대체한다.

  로그인  : POST /api/v1/auth/login → {user, access, refresh} 를 세션에 저장
  요청마다: 미들웨어가 세션을 읽어 request.web_user 를 세팅
  권한    : @owner_required 가 request.web_user.role 을 확인

JWT 는 세션(Redis)에만 존재하고 브라우저로 내려가지 않는다. 브라우저가 갖는 건
세션 ID 쿠키뿐이다.
"""

from functools import wraps
from urllib.parse import quote

from django.shortcuts import redirect

from .api_client import SESSION_KEY, ApiClient

ROLE_LABELS = {
    'user': '일반회원',
    'owner': '점주',
    'rider': '라이더',
    'admin': '관리자',
}


class WebUser:
    """템플릿과 뷰에서 쓰는 읽기 전용 사용자 표현.

    django.contrib.auth 의 User 를 흉내내지만 DB 접근은 전혀 없다.
    값은 로그인 응답의 `user` 딕셔너리가 전부다.
    """

    def __init__(self, data=None):
        self._data = data or {}

    def __bool__(self):
        return bool(self._data)

    def __getattr__(self, name):
        # 템플릿에서 없는 필드를 참조해도 예외 대신 빈 문자열이 되도록 한다.
        if name.startswith('_'):
            raise AttributeError(name)
        return self._data.get(name, '')

    @property
    def is_authenticated(self):
        return bool(self._data)

    @property
    def is_anonymous(self):
        return not self._data

    @property
    def role(self):
        return self._data.get('role', '')

    def get_role_display(self):
        return ROLE_LABELS.get(self.role, self.role or '-')

    def __str__(self):
        return self._data.get('username', '')


class ApiSessionMiddleware:
    """AuthenticationMiddleware 대체. request.web_user 를 세팅한다."""

    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        auth = request.session.get(SESSION_KEY) or {}
        request.web_user = WebUser(auth.get('user'))
        return self.get_response(request)


def login_session(request, payload):
    """로그인 API 응답을 세션에 저장한다."""
    # 세션 고정(fixation) 방지: 권한이 바뀌는 시점에 세션 키를 새로 판다.
    request.session.cycle_key()
    request.session[SESSION_KEY] = {
        'user': payload.get('user') or {},
        'access': payload.get('access'),
        'refresh': payload.get('refresh'),
    }
    request.session.modified = True
    request.web_user = WebUser(payload.get('user'))


def logout_session(request):
    """API 쪽 refresh 를 블랙리스트 처리하고 로컬 세션을 비운다."""
    ApiClient(session=request.session).logout()
    request.session.flush()
    request.web_user = WebUser()


def _redirect_login(request):
    return redirect(f'/web/login?next={quote(request.get_full_path())}')


def role_required(*roles):
    """로그인 + 지정한 role 중 하나여야 접근 가능."""

    def decorator(view):
        @wraps(view)
        def wrapper(request, *args, **kwargs):
            user = getattr(request, 'web_user', None)
            if not user or not user.is_authenticated:
                return _redirect_login(request)
            if roles and user.role not in roles:
                # 이 컨테이너는 점주 전용이다. 다른 역할이면 로그인 화면으로 되돌린다.
                logout_session(request)
                return _redirect_login(request)
            return view(request, *args, **kwargs)

        return wrapper

    return decorator


owner_required = role_required('owner')
