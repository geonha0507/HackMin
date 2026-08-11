"""요청/응답 본문을 하이브리드 암호화하는 DRF Parser/Renderer + 강제 미들웨어.

구성:
  - EncryptedJSONParser   : 요청에 X-Enc-Key 가 있으면 {iv,data} 봉투를 복호화
  - SignedMultiPartParser : multipart 본문의 실제 해시가 서명된 값과 같은지 검증
  - EncryptedJSONRenderer : 같은 세션키로 응답 JSON 을 GCM 암호화
  - PayloadEnforcementMiddleware: 신뢰되지 않은 클라이언트(=앱/공격자)는 반드시
    암호화 + 유효한 HMAC 서명(X-Sig)을 붙이도록 강제. 서버측 BFF 는 X-Internal-Key
    로 식별해 평문을 허용(SSR 호환 유지).

클라이언트 구분:
  - 서버측 BFF(web_bff/admin_bff) : httpx 로 X-Internal-Key 를 붙여 호출 → 평문 허용.
    이 키는 서버 환경변수에만 있고 APK·브라우저·리포에는 없다.
  - Android 앱                    : X-Enc-Key + X-Body-Sha256 + X-Sig 를 붙여 호출.
  - 그 외(Burp 로 헤더 떼거나 위조 시도) : 셋 다 없으므로 400 으로 거부.

헤더:
  요청 X-Enc-Key     = base64(RSA-OAEP(AES키))
      X-Body-Sha256  = base64(sha256(원본 본문 바이트))
      X-Sig          = base64(HMAC-SHA256("METHOD\\nFULL_PATH\\nX-Enc-Key\\nBODY_SHA256"))
      본문           = {"iv","data"}  (multipart 는 예외적으로 평문)
  응답 X-Enc = "1", 본문 {"iv","data"}

**본문 무결성 2단 검증** — 미들웨어는 스트림을 건드리지 않아야 하므로(대용량
멀티파트 업로드 보호) 역할을 나눈다:
  1) 미들웨어 : X-Body-Sha256 이 서명에 묶여 있는지 확인 (헤더만 봄, 값은 '주장')
  2) 파서     : 실제 본문을 읽으면서 해시를 계산해 그 '주장'과 대조
두 단계를 모두 통과해야 뷰에 도달한다. 그래서 서명이 붙지 않은 본문은 존재할 수 없고,
본문이 검증되지 않는 파서로 새는 것을 막기 위해 Content-Type 도 화이트리스트로 제한한다.
"""
import base64
import hashlib
import json

from django.conf import settings
from django.http import JsonResponse
from django.utils.crypto import constant_time_compare

from rest_framework.exceptions import ParseError
from rest_framework.parsers import JSONParser, MultiPartParser
from rest_framework.renderers import JSONRenderer

from . import crypto

# Django는 요청 헤더를 META['HTTP_...'] 로 노출한다.
ENC_KEY_META = 'HTTP_X_ENC_KEY'
SIG_META = 'HTTP_X_SIG'
BODY_SHA_META = 'HTTP_X_BODY_SHA256'
INTERNAL_KEY_META = 'HTTP_X_INTERNAL_KEY'
# 응답이 암호화됐음을 앱에 알리는 헤더.
ENC_FLAG_HEADER = 'X-Enc'

# 비신뢰(앱) 요청에서 허용하는 Content-Type. 본문 해시를 실제로 검증하는 파서가
# 있는 타입만 허용한다. 그 외 타입은 본문이 무검증으로 통과할 수 있어 막는다.
VERIFIED_CONTENT_TYPES = ('application/json', 'multipart/form-data')


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


def _claimed_digest(request):
    """앱이 주장한 본문 해시(X-Body-Sha256)."""
    return request.META.get(BODY_SHA_META, '') if request is not None else ''


def _assert_digest(request, raw):
    """실제 본문 해시가 서명된 값(X-Body-Sha256)과 일치하는지 확인.

    미들웨어에서 이미 '그 값이 서명에 묶여 있다'는 것을 검증했으므로,
    여기서 실제 바이트와 대조하면 본문 무결성이 완성된다.
    """
    claimed = _claimed_digest(request)
    if not claimed:
        raise ParseError('본문 해시 헤더(X-Body-Sha256)가 없습니다.')
    if not constant_time_compare(crypto.body_digest(raw), claimed):
        raise ParseError('본문이 서명된 해시와 일치하지 않습니다.')


class EncryptedJSONParser(JSONParser):
    """X-Enc-Key가 있으면 {iv,data} 봉투를 복호화해 JSON으로 파싱.

    평문 폴백은 없다. 헤더가 있으면 본문은 반드시 봉투여야 한다.
    (폴백을 두면 캡처한 서명을 재사용해 평문 본문을 밀어 넣을 수 있다.)
    """

    media_type = 'application/json'

    def parse(self, stream, media_type=None, parser_context=None):
        parser_context = parser_context or {}
        request = parser_context.get('request')
        enc_key_b64 = request.META.get(ENC_KEY_META) if request is not None else None
        if not enc_key_b64:
            # 비암호화 요청(내부 BFF 등) → 기본 JSON 파싱
            return super().parse(stream, media_type, parser_context)

        raw = stream.read()
        _assert_digest(request, raw)

        try:
            session_key = crypto.unwrap_session_key(enc_key_b64)
        except crypto.PayloadError as exc:
            raise ParseError(str(exc))
        if request is not None:
            request._enc_session_key = session_key  # 응답 렌더러가 재사용

        if not raw:
            return {}
        try:
            envelope = json.loads(raw.decode('utf-8'))
        except (UnicodeDecodeError, ValueError) as exc:
            raise ParseError('봉투 JSON 파싱 실패: %s' % exc)
        if not crypto.is_envelope(envelope):
            raise ParseError('암호화된 본문({"iv","data"})이 필요합니다.')

        try:
            plaintext = crypto.decrypt_body(session_key, envelope['iv'], envelope['data'])
        except crypto.PayloadError as exc:
            raise ParseError(str(exc))

        if not plaintext:
            return {}
        try:
            return json.loads(plaintext.decode('utf-8'))
        except (UnicodeDecodeError, ValueError) as exc:
            raise ParseError('복호화된 JSON 파싱 실패: %s' % exc)


class _HashingStream:
    """읽는 바이트를 SHA-256 으로 누적하는 스트림 래퍼(멀티파트 스트리밍 유지)."""

    def __init__(self, stream):
        self._stream = stream
        self._hash = hashlib.sha256()

    def read(self, size=-1):
        chunk = self._stream.read(size)
        if chunk:
            self._hash.update(chunk)
        return chunk

    def digest_b64(self):
        # 파서가 끝까지 읽지 않았을 수 있으므로 남은 바이트를 마저 흘려보낸다.
        while True:
            chunk = self._stream.read(65536)
            if not chunk:
                break
            self._hash.update(chunk)
        return base64.b64encode(self._hash.digest()).decode('ascii')


class SignedMultiPartParser(MultiPartParser):
    """멀티파트 본문은 평문으로 나가므로, 실제 바이트 해시를 서명값과 대조한다.

    본문 전체를 메모리에 올리지 않고 스트리밍하며 해시를 계산한다.
    """

    def parse(self, stream, media_type=None, parser_context=None):
        parser_context = parser_context or {}
        request = parser_context.get('request')
        if request is None or not request.META.get(ENC_KEY_META):
            # 내부 BFF 등 비암호화 클라이언트 → 기존 동작 유지
            return super().parse(stream, media_type, parser_context)

        hashing = _HashingStream(stream)
        result = super().parse(hashing, media_type, parser_context)

        claimed = _claimed_digest(request)
        if not claimed:
            raise ParseError('본문 해시 헤더(X-Body-Sha256)가 없습니다.')
        if not constant_time_compare(hashing.digest_b64(), claimed):
            raise ParseError('본문이 서명된 해시와 일치하지 않습니다.')

        # 응답 암호화를 위해 세션키를 확보해 둔다.
        _session_key_from_request(request)
        return result


class EncryptedJSONRenderer(JSONRenderer):
    """X-Enc-Key 요청엔 응답 JSON을 세션키로 GCM 암호화해 봉투로 반환."""

    media_type = 'application/json'
    format = 'json'

    def render(self, data, accepted_media_type=None, renderer_context=None):
        rendered = super().render(data, accepted_media_type, renderer_context)  # 평문 JSON bytes
        renderer_context = renderer_context or {}
        request = renderer_context.get('request')
        response = renderer_context.get('response')

        # 본문이 없는 응답(204 No Content 등)은 절대 건드리지 않는다.
        # 암호화하면 빈 본문이 아니게 되어 OkHttp 가
        # "HTTP 204 had non-zero Content-Length" 로 끊는다.
        if not rendered:
            return rendered

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


def _reject(code, message, status=400):
    return JsonResponse({'code': code, 'message': message}, status=status)


class PayloadEnforcementMiddleware:
    """앱(비신뢰) 요청은 암호화 + 유효 HMAC 서명을 강제한다.

    - 신뢰 BFF(X-Internal-Key)              : 통과(평문 허용)
    - 앱(X-Enc-Key + X-Body-Sha256 + X-Sig) : 서명 검증 후 통과
    - 그 외                                  : 400 거부

    본문 스트림은 건드리지 않고 헤더/메서드/경로만 본다(멀티파트 업로드 안전).
    실제 본문이 X-Body-Sha256 과 같은지는 파서가 확인한다.
    PAYLOAD_ENFORCE=False 면 전 구간 비활성(개발/디버깅용).
    """

    def __init__(self, get_response):
        self.get_response = get_response
        self.enabled = getattr(settings, 'PAYLOAD_ENFORCE', False)
        self.exempt_prefixes = tuple(getattr(settings, 'PAYLOAD_ENFORCE_EXEMPT_PREFIXES', ()))

    def __call__(self, request):
        if self._should_enforce(request):
            rejection = self._check(request)
            if rejection is not None:
                return rejection
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

    def _check(self, request):
        """통과면 None, 거부면 JsonResponse."""
        if _is_internal(request):
            return None

        enc_key = request.META.get(ENC_KEY_META)
        sig = request.META.get(SIG_META)
        body_sha = request.META.get(BODY_SHA_META)
        if not enc_key or not sig or not body_sha:
            return _reject('encryption_required',
                           '암호화되지 않은 요청은 허용되지 않습니다.')

        # 본문 해시를 실제로 검증할 수 있는 Content-Type 만 허용한다.
        # (검증 파서가 없는 타입으로 우회해 무검증 본문을 밀어 넣는 것을 막는다)
        content_type = (request.META.get('CONTENT_TYPE') or '').split(';')[0].strip().lower()
        if content_type and content_type not in VERIFIED_CONTENT_TYPES:
            return _reject('unsupported_content_type',
                           '지원하지 않는 Content-Type 입니다.', status=415)

        if not crypto.verify_app_signature(
                request.method, request.get_full_path(), enc_key, body_sha, sig):
            return _reject('bad_signature', '요청 서명이 유효하지 않습니다.', status=401)
        return None
