"""페이로드 하이브리드 암호화 + 앱 요청 서명 헬퍼.

두 가지 비밀을 다룬다:

1. **서버 RSA 개인키** (`PAYLOAD_PRIVATE_KEY_PEM`) — 앱이 요청마다 만든 AES-256
   세션키 K 를 서버 공개키로 RSA-OAEP 래핑해 `X-Enc-Key` 로 보내면, 서버가
   개인키로 K 를 복원(unwrap)해 본문을 복호화하고 같은 K 로 응답을 암호화한다.
   Burp 등 네트워크 프록시는 개인키가 없어 K 를 복원할 수 없다.

2. **앱 HMAC 시크릿** (`PAYLOAD_APP_HMAC_SECRET`) — 공개키는 누구나 알 수 있어
   봉투 자체는 위조가 가능하다. 그래서 앱은 요청마다 HMAC-SHA256 서명(`X-Sig`)을
   붙이고 서버가 검증한다. 이 시크릿은 **앱 바이너리에만** 있어야 하며(리포에
   커밋 금지) 서버에는 환경변수로만 주입한다.

앱(Java) 과 파라미터를 정확히 맞춰야 상호운용된다:
  - RSA/ECB/OAEPWithSHA-256AndMGF1Padding  (hash=SHA-256, MGF1=SHA-256)
  - AES/GCM/NoPadding, 256bit key, 12-byte IV, 128-bit tag(ct 뒤에 부착)
  - HMAC-SHA256(secret, "METHOD\\nFULL_PATH\\nX-Enc-Key\\nBODY_SHA256") → base64

서명에 **본문 해시를 포함**하는 것이 핵심이다. 서명이 헤더만 묶으면, 캡처한
(X-Enc-Key, X-Sig) 쌍을 재사용해 같은 METHOD+PATH 로 임의 본문을 보낼 수 있다.
BODY_SHA256 = base64(sha256(네트워크로 나간 원본 바이트)) — JSON 이면 봉투 JSON,
multipart 면 multipart 원문, 본문이 없으면 빈 바이트의 해시.
"""
import base64
import binascii
import hashlib
import hmac
import os

from django.conf import settings
from django.utils.crypto import constant_time_compare

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

_private_key = None

_OAEP = padding.OAEP(
    mgf=padding.MGF1(algorithm=hashes.SHA256()),
    algorithm=hashes.SHA256(),
    label=None,
)


class PayloadError(Exception):
    """복호화·서명 관련 오류. 호출부에서 400 으로 변환한다."""


def _load_private_key():
    """서버 개인키를 지연 로드(캐시). settings.PAYLOAD_PRIVATE_KEY_PEM 사용."""
    global _private_key
    if _private_key is not None:
        return _private_key
    pem = getattr(settings, 'PAYLOAD_PRIVATE_KEY_PEM', '')
    if not pem:
        raise RuntimeError(
            'PAYLOAD_PRIVATE_KEY_PEM 미설정 — 페이로드 복호화 불가. '
            '환경변수 PAYLOAD_PRIVATE_KEY_B64 또는 keys/payload_private_dev.pem 를 확인하세요.'
        )
    if isinstance(pem, str):
        pem = pem.encode('utf-8')
    _private_key = serialization.load_pem_private_key(pem, password=None)
    return _private_key


def unwrap_session_key(enc_key_b64):
    """X-Enc-Key(base64, RSA-OAEP로 감싼 AES키) → raw AES 세션키 bytes.

    입력이 잘못된 경우는 클라이언트 잘못이므로 PayloadError 로 올린다.
    (개인키 미설정 같은 서버 설정 오류는 RuntimeError 로 그대로 둔다 — 500 이 맞다.)
    """
    key_obj = _load_private_key()  # 설정 오류면 RuntimeError → 500
    try:
        enc_key = base64.b64decode(enc_key_b64, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise PayloadError('X-Enc-Key base64 디코딩 실패') from exc
    try:
        key = key_obj.decrypt(enc_key, _OAEP)
    except Exception as exc:  # ValueError 등 — 라이브러리별로 타입이 다르다
        raise PayloadError('세션키 복호화 실패') from exc
    if len(key) not in (16, 24, 32):
        raise PayloadError('세션키 길이 오류: %d' % len(key))
    return key


def decrypt_body(session_key, iv_b64, data_b64):
    """AES-256-GCM 복호화 → 평문 bytes. data = ciphertext || tag(16B)."""
    try:
        iv = base64.b64decode(iv_b64, validate=True)
        data = base64.b64decode(data_b64, validate=True)
    except (binascii.Error, ValueError, TypeError) as exc:
        raise PayloadError('봉투 base64 디코딩 실패') from exc
    try:
        return AESGCM(session_key).decrypt(iv, data, None)
    except Exception as exc:  # InvalidTag 포함 — 본문이 변조됐다는 뜻
        raise PayloadError('본문 복호화 실패(무결성 검증 불통과)') from exc


def encrypt_body(session_key, plaintext):
    """AES-256-GCM 암호화 → envelope dict {'iv','data'} (둘 다 base64)."""
    if isinstance(plaintext, str):
        plaintext = plaintext.encode('utf-8')
    iv = os.urandom(12)
    ct = AESGCM(session_key).encrypt(iv, plaintext, None)  # ct || tag
    return {
        'iv': base64.b64encode(iv).decode('ascii'),
        'data': base64.b64encode(ct).decode('ascii'),
    }


def is_envelope(obj):
    """{'iv','data'} 형태의 암호문 봉투인지."""
    return isinstance(obj, dict) and 'iv' in obj and 'data' in obj


# --- 본문 해시 -------------------------------------------------------------

def body_digest(raw):
    """네트워크로 오간 원본 바이트의 base64(SHA-256). 본문이 없으면 빈 바이트 기준.

    앱 PayloadCrypto.sha256B64 와 반드시 동일해야 한다.
    """
    if raw is None:
        raw = b''
    if isinstance(raw, str):
        raw = raw.encode('utf-8')
    return base64.b64encode(hashlib.sha256(raw).digest()).decode('ascii')


EMPTY_BODY_DIGEST = body_digest(b'')


# --- 앱 요청 서명 (HMAC-SHA256) -------------------------------------------

def _app_hmac_secret():
    """앱과 공유하는 HMAC 시크릿(bytes). 문자열을 UTF-8 로 인코딩해 키로 쓴다."""
    secret = getattr(settings, 'PAYLOAD_APP_HMAC_SECRET', '')
    if not secret:
        return b''
    if isinstance(secret, str):
        secret = secret.encode('utf-8')
    return secret


def sign_request(method, full_path, enc_key_b64, body_sha256_b64):
    """앱과 동일 규약으로 서명 문자열을 만든 뒤 HMAC-SHA256 → base64.

    서명 대상: "METHOD\\nFULL_PATH\\nX-Enc-Key\\nBODY_SHA256"
      - METHOD      : HTTP 메서드(대문자)
      - FULL_PATH   : 쿼리스트링 포함 경로 (예: /api/v1/orders?page=2)
      - X-Enc-Key   : RSA-OAEP 로 감싼 세션키 base64 (요청마다 랜덤)
      - BODY_SHA256 : base64(sha256(원본 본문 바이트)). 본문 없으면 빈 바이트 해시.

    본문 해시를 포함해야 캡처한 서명을 다른 본문에 재사용할 수 없다.
    (GCM tag 는 봉투를 쓸 때만 유효하고, multipart 는 평문으로 나가므로
     본문 무결성을 서명이 직접 책임져야 한다.)
    """
    msg = '{}\n{}\n{}\n{}'.format(
        method, full_path, enc_key_b64, body_sha256_b64).encode('utf-8')
    mac = hmac.new(_app_hmac_secret(), msg, hashlib.sha256).digest()
    return base64.b64encode(mac).decode('ascii')


def verify_app_signature(method, full_path, enc_key_b64, body_sha256_b64, sig_b64):
    """앱 서명 검증. 시크릿 미설정이거나 값이 비면 False(=거부)."""
    if not _app_hmac_secret() or not sig_b64 or not enc_key_b64 or not body_sha256_b64:
        return False
    expected = sign_request(method, full_path, enc_key_b64, body_sha256_b64)
    return constant_time_compare(expected, sig_b64)
