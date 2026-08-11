"""요청/응답 본문을 하이브리드 암호화하는 DRF Parser/Renderer + 강제 미들웨어.

구성:
  - EncryptedJSONParser  : 요청에 X-Enc-Key 가 있으면 {iv,data} 봉투를 복호화
  - EncryptedJSONRenderer: 같은 세션키로 응답 JSON 을 GCM 암호화
  - PayloadEnforcementMiddleware: 신뢰되지 않은 클라이언트(=앱/공격자)는 반드시
    암호화 + 유효한 HMAC 서명(X-Sig)을 붙이도록 강제. 서버측 BFF 는 X-Internal-Key
    로 식별해 평문을 허용(SSR 호환 유지).

클라이언트 구분:
  - 서버측 BFF(web_bff/admin_bff) : httpx 로 X-Internal-Key 를 붙여 호출 → 평문 허용.
    이 키는 서버 환경변수에만 있고 APK·브라우저·리포에는 없다.
  - Android 앱                    : X-Enc-Key(암호화) + X-Sig(HMAC) 를 붙여 호출.
  - 그 외(Burp 로 헤더 떼거나 위조 시도) : 둘 다 없으므로 400 으로 거부.

헤더:
  요청 X-Enc-Key = base64(RSA-OAEP(AES키)),  X-Sig = base64(HMAC),  본문 {"iv","data"}
  응답 X-Enc     = "1",                       본문 {"iv","data"}
"""
import json

from django.conf import settings
from django.http import JsonResponse
from django.utils.crypto import constant_time_compare

from rest_framework.parsers import JSONParser
from rest_framework.renderers import JSONRenderer

from . import crypto

# Django는 요청 헤더를 META['HTTP_...'] 로 노출한다.
ENC_KEY_META = 'HTTP_X_ENC_KEY'
SIG_META = 'HTTP_X_SIG'
INTERNAL_KEY_META = 'HTTP_X_INTERNAL_KEY'
# 응답이 암호화됐음을 앱에 알리는 헤더.
ENC_FLAG_HEADER = 'X-Enc'


def _session_key_from_request(request):
    """요청에 보관된 세션키(파서가 넣어둠) 우선, 없으면 X-Enc-Key 헤더에서 복원."""
    if request is None:
        return None
    key = getattr(request, '_enc_session_key', None)
    if key is not None:
        return key
    enc_key_b64 = request.META.get(ENC_KEY_META)
    if not enc_key_b64:
        return None
    try:
        key = crypto.unwrap_session_key(enc_key_b64)
    except Exception:
        return None
    try:
        request._enc_session_key = key  # 렌더러가 재사용
    except Exception:
        pass
    return key


class EncryptedJSONParser(JSONParser):
    """X-Enc-Key가 있으면 {iv,data} 봉투를 복호화해 JSON으로 파싱."""

    media_type = 'application/json'

    def parse(self, stream, media_type=None, parser_context=None):
        parser_context = parser_context or {}
        request = parser_context.get('request')
        enc_key_b64 = request.META.get(ENC_KEY_META) if request is not None else None
        if not enc_key_b64:
            # 비암호화 요청(내부 BFF 등) → 기본 JSON 파싱
            return super().parse(stream, media_type, parser_context)

        session_key = crypto.unwrap_session_key(enc_key_b64)
        if request is not None:
            request._enc_session_key = session_key  # 응답 렌더러가 재사용

        raw = stream.read()
        if not raw:
            return {}
        envelope = json.loads(raw.decode('utf-8'))
        if not crypto.is_envelope(envelope):
            # 헤더는 있으나 본문이 봉투가 아니면 평문 JSON으로 취급
            return envelope
        plaintext = crypto.decrypt_body(session_key, envelope['iv'], envelope['data'])
        if not plaintext:
            return {}
        return json.loads(plaintext.decode('utf-8'))


class EncryptedJSONRenderer(JSONRenderer):
    """X-Enc-Key 요청엔 응답 JSON을 세션키로 GCM 암호화해 봉투로 반환."""

    media_type = 'application/json'
    format = 'json'

    def render(self, data, accepted_media_type=None, renderer_context=None):
        rendered = super().render(data, accepted_media_type, renderer_context)  # 평문 JSON bytes
        renderer_context = renderer_context or {}
        request = renderer_context.get('request')
        response = renderer_context.get('response')

        session_key = _session_key_from_request(request)
        if session_key is None:
            return rendered  # 비암호화 클라이언트 → 평문 JSON 그대로

        envelope = crypto.encrypt_body(session_key, rendered)
        if response is not None:
            response[ENC_FLAG_HEADER] = '1'
        return json.dumps(envelope).encode('utf-8')


# --- 암호화 강제 미들웨어 --------------------------------------------------

def _is_internal(request):
    """서버측 BFF 가 붙인 유효한 X-Internal-Key 인지."""
    secret = getattr(settings, 'PAYLOAD_INTERNAL_KEY', '')
    if not secret:
        return False
    presented = request.META.get(INTERNAL_KEY_META, '')
    return bool(presented) and constant_time_compare(presented, secret)


def _reject(code, message):
    return JsonResponse({'code': code, 'message': message}, status=400)


class PayloadEnforcementMiddleware:
    """앱(비신뢰) 요청은 암호화 + 유효 HMAC 서명을 강제한다.

    - 신뢰 BFF(X-Internal-Key) : 통과(평문 허용)
    - 앱(X-Enc-Key + X-Sig)    : 서명 검증 후 통과
    - 그 외                     : 400 거부

    본문 스트림은 건드리지 않고 헤더/메서드/경로만 본다(멀티파트 업로드 안전).
    PAYLOAD_ENFORCE=False 면 전 구간 비활성(개발/디버깅용).
    """

    def __init__(self, get_response):
        self.get_response = get_response
        self.enabled = getattr(settings, 'PAYLOAD_ENFORCE', False)
        self.exempt_prefixes = tuple(getattr(settings, 'PAYLOAD_ENFORCE_EXEMPT_PREFIXES', ()))

    def __call__(self, request):
        if self._should_enforce(request) and not self._authorized(request):
            enc_key = request.META.get(ENC_KEY_META)
            sig = request.META.get(SIG_META)
            if not enc_key or not sig:
                return _reject('encryption_required',
                               '암호화되지 않은 요청은 허용되지 않습니다.')
            return _reject('bad_signature', '요청 서명이 유효하지 않습니다.')
        request._enc_internal = _is_internal(request)
        return self.get_response(request)

    def _should_enforce(self, request):
        if not self.enabled:
            return False
        if request.method == 'OPTIONS':  # CORS 프리플라이트
            return False
        path = request.path
        if not path.startswith('/api/'):
            return False
        if path.startswith(self.exempt_prefixes):
            return False
        return True

    def _authorized(self, request):
        if _is_internal(request):
            return True
        enc_key = request.META.get(ENC_KEY_META)
        sig = request.META.get(SIG_META)
        return crypto.verify_app_signature(
            request.method, request.get_full_path(), enc_key or '', sig or '',
        )
