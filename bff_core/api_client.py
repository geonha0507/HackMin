"""백엔드 API(/api/v1) 호출 레이어.

web_bff 의 모든 데이터 접근은 이 모듈을 거친다. 뷰에서 ORM 을 쓰지 않는다.

책임:
  - 세션에 보관된 access 토큰을 Authorization 헤더로 주입
  - 401 이 오면 refresh 토큰으로 한 번 재발급 후 재시도
  - API 에러를 ApiError 로 정규화 (뷰가 HTTP 상태코드에 직접 의존하지 않도록)
"""

import logging

import httpx
from django.conf import settings

logger = logging.getLogger(__name__)

# X-Internal-Key: 서버측 BFF 임을 API 에 증명하는 내부 호출 키. 이 헤더가 유효하면
# API 의 PayloadEnforcementMiddleware 가 평문 호출을 허용한다(앱은 암호화 강제).
# 키는 서버 환경변수에만 있고 APK·브라우저로는 절대 나가지 않는다.
# web_bff/admin_bff 는 config.settings 를 쓰지 않으므로(각자 standalone settings),
# API 서버와 동일한 env·기본값을 여기서 직접 읽어 단일 소스로 맞춘다.
import os

PAYLOAD_INTERNAL_KEY = os.environ.get(
    'PAYLOAD_INTERNAL_KEY', 'Z90pA78Pyhwpd/ZRpfsuRV91Itg/yuD1Joulvp/IG0w=')

# httpx.Client 는 스레드 안전하므로 프로세스당 하나를 재사용한다 (커넥션 풀링).
_client = httpx.Client(
    base_url=settings.API_BASE_URL,
    timeout=settings.API_TIMEOUT,
    headers={'X-Internal-Key': PAYLOAD_INTERNAL_KEY},
)

SESSION_KEY = 'hackmin_auth'


class ApiError(Exception):
    """API 가 4xx/5xx 를 반환했거나 통신 자체가 실패한 경우."""

    def __init__(self, status_code, code='', message='', payload=None):
        self.status_code = status_code
        self.code = code
        self.message = message or '요청을 처리하지 못했습니다.'
        self.payload = payload or {}
        super().__init__(f'[{status_code}] {code}: {self.message}')

    @property
    def is_auth_error(self):
        return self.status_code in (401, 403)


def _parse_error(response):
    try:
        body = response.json()
    except ValueError:
        # 상류 응답이 JSON 이 아니면(예: API 의 500 HTML 페이지) 원문을 사용자에게
        # 그대로 노출하지 않는다. 일반 메시지로 대체한다.
        return ApiError(response.status_code, 'invalid_response', '요청을 처리하지 못했습니다.')

    if isinstance(body, dict):
        # common.exceptions 형식: {"code": "...", "message": "..."}
        # (hackmin_exception_handler 와 error_response 가 모두 이 평평한 형태로 낸다)
        if 'message' in body:
            return ApiError(
                response.status_code, str(body.get('code') or ''), str(body['message']), body,
            )

        # 예외 핸들러를 타지 않은 응답용 대비책: {"error": {"code", "message"}}
        err = body.get('error')
        if isinstance(err, dict):
            return ApiError(response.status_code, err.get('code', ''), err.get('message', ''), body)

        # DRF 기본 형식: {"detail": "..."} 또는 필드별 에러
        if 'detail' in body:
            return ApiError(response.status_code, '', str(body['detail']), body)

    return ApiError(response.status_code, '', '요청을 처리하지 못했습니다.', body)


class ApiClient:
    """요청 하나의 수명 동안 쓰는 클라이언트. django session 을 물고 있다."""

    def __init__(self, session=None):
        self.session = session

    # -- 토큰 ---------------------------------------------------------------
    @property
    def _auth(self):
        return (self.session or {}).get(SESSION_KEY) or {}

    def _save_auth(self, auth):
        if self.session is not None:
            self.session[SESSION_KEY] = auth
            self.session.modified = True

    def _headers(self, extra=None):
        headers = dict(extra or {})
        access = self._auth.get('access')
        if access:
            headers['Authorization'] = f'Bearer {access}'
        return headers

    def _try_refresh(self):
        """refresh 토큰으로 access 재발급. 성공하면 True."""
        refresh = self._auth.get('refresh')
        if not refresh:
            return False
        try:
            resp = _client.post('/auth/refresh', json={'refresh': refresh})
        except httpx.HTTPError:
            return False
        if resp.status_code != 200:
            return False
        auth = dict(self._auth)
        auth['access'] = resp.json().get('access')
        self._save_auth(auth)
        return True

    # -- 요청 ---------------------------------------------------------------
    def request(self, method, path, *, params=None, json=None, files=None, data=None,
                authed=True, _retried=False):
        headers = self._headers() if authed else {}
        try:
            resp = _client.request(
                method, path, params=params, json=json, files=files, data=data, headers=headers,
            )
        except httpx.HTTPError as exc:
            logger.warning('API 호출 실패 %s %s: %s', method, path, exc)
            raise ApiError(503, 'api_unreachable', '백엔드 API에 연결하지 못했습니다.') from exc

        if resp.status_code == 401 and authed and not _retried and self._try_refresh():
            return self.request(method, path, params=params, json=json, files=files,
                                data=data, authed=authed, _retried=True)

        if resp.status_code >= 400:
            raise _parse_error(resp)

        if resp.status_code == 204 or not resp.content:
            return {}
        return resp.json()

    def raw(self, method, path, *, params=None, _retried=False):
        """JSON 파싱 없이 httpx 응답 객체를 그대로 돌려준다.

        파일 다운로드 중계에 쓴다. 브라우저는 JWT 를 갖고 있지 않으므로
        API 의 다운로드 URL 을 직접 열 수 없다. web_bff 가 대신 받아서
        스트림을 그대로 넘겨준다.
        """
        try:
            resp = _client.request(method, path, params=params, headers=self._headers())
        except httpx.HTTPError as exc:
            logger.warning('API 다운로드 실패 %s %s: %s', method, path, exc)
            raise ApiError(503, 'api_unreachable', '백엔드 API에 연결하지 못했습니다.') from exc

        if resp.status_code == 401 and not _retried and self._try_refresh():
            return self.raw(method, path, params=params, _retried=True)
        if resp.status_code >= 400:
            raise _parse_error(resp)
        return resp

    def get(self, path, **kw):
        return self.request('GET', path, **kw)

    def post(self, path, **kw):
        return self.request('POST', path, **kw)

    def put(self, path, **kw):
        return self.request('PUT', path, **kw)

    def delete(self, path, **kw):
        return self.request('DELETE', path, **kw)

    # -- 인증 ---------------------------------------------------------------
    def login(self, username, password):
        """성공 시 {'user': {...}, 'access': ..., 'refresh': ...} 반환."""
        return self.request(
            'POST', '/auth/login',
            json={'username': username, 'password': password},
            authed=False,
        )

    def logout(self):
        """API 쪽 refresh 토큰 블랙리스트 처리. 실패해도 로컬 세션은 지운다."""
        refresh = self._auth.get('refresh')
        if not refresh:
            return
        try:
            self.request('POST', '/auth/logout', json={'refresh': refresh})
        except ApiError as exc:
            logger.info('API 로그아웃 실패(무시): %s', exc)


def client_for(request):
    return ApiClient(session=request.session)
