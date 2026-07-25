"""요청/응답 본문을 하이브리드 암호화하는 DRF Parser/Renderer.

듀얼 모드: 요청에 X-Enc-Key 헤더가 있으면 암호화 모드로 동작하고,
없으면 평범한 JSON으로 폴백한다(비암호화 클라이언트·테스트 호환).

  요청  : 헤더 X-Enc-Key = base64(RSA-OAEP(AES키)),  본문 = {"iv","data"}(JSON일 때)
  응답  : 헤더 X-Enc = "1",                          본문 = {"iv","data"}
"""
import json

from rest_framework.parsers import JSONParser
from rest_framework.renderers import JSONRenderer

from . import crypto

# Django는 요청 헤더 X-Enc-Key 를 META['HTTP_X_ENC_KEY'] 로 노출한다.
ENC_KEY_META = 'HTTP_X_ENC_KEY'
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
            # 비암호화 요청 → 기본 JSON 파싱
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
