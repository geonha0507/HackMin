"""페이로드 하이브리드 암호화 + 앱 요청 서명 헬퍼.

두 가지 비밀을 다룬다:

1. **서버 RSA 개인키** (`PAYLOAD_PRIVATE_KEY_PEM`) — 앱이 요청마다 만든 AES-256
   세션키 K 를 서버 공개키로 RSA-OAEP 래핑해 `X-Enc-Key` 로 보내면, 서버가
   개인키로 K 를 복원(unwrap)해 본문을 복호화하고 같은 K 로 응답을 암호화한다.
   Burp 등 네트워크 프록시는 개인키가 없어 K 를 복원할 수 없다.

2. **앱 HMAC 시크릿** (`PAYLOAD_APP_HMAC_SECRET`) — 공개키는 누구나 알 수 있어
   봉투 자체는 위조가 가능하다. 그래서 앱은 요청마다 HMAC-SHA256 서명(`X-Sig`)을
   붙이고 서버가 검증한다. 이 시크릿은 **앱 바이너리에만** 있으므로(리포·브라우저
   에는 없음) 유효 요청을 만들려면 APK 를 리버싱하거나 런타임 후킹(Frida)해야 한다.
   → "Burp 만으로는 안 된다"는 명분을 만드는 핵심.

앱(Java) 과 파라미터를 정확히 맞춰야 상호운용된다:
  - RSA/ECB/OAEPWithSHA-256AndMGF1Padding  (hash=SHA-256, MGF1=SHA-256)
  - AES/GCM/NoPadding, 256bit key, 12-byte IV, 128-bit tag(ct 뒤에 부착)
  - HMAC-SHA256(secret, "METHOD\nFULL_PATH\nX-Enc-Key") → base64
"""
import base64
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


def _load_private_key():
    """서버 개인키를 지연 로드(캐시). settings.PAYLOAD_PRIVATE_KEY_PEM 사용."""
    global _private_key
    if _private_key is not None:
        return _private_key
    pem = getattr(settings, 'PAYLOAD_PRIVATE_KEY_PEM', '')
    if not pem:
        raise RuntimeError(
            'PAYLOAD_PRIVATE_KEY_PEM 미설정 — 페이로드 복호화 불가. '
            '환경변수 또는 keys/payload_private_dev.pem 를 확인하세요.'
        )
    if isinstance(pem, str):
        pem = pem.encode('utf-8')
    _private_key = serialization.load_pem_private_key(pem, password=None)
    return _private_key


def unwrap_session_key(enc_key_b64):
    """X-Enc-Key(base64, RSA-OAEP로 감싼 AES키) → raw AES 세션키 bytes."""
    enc_key = base64.b64decode(enc_key_b64)
    key = _load_private_key().decrypt(enc_key, _OAEP)
    if len(key) not in (16, 24, 32):
        raise ValueError('세션키 길이 오류: %d' % len(key))
    return key


def decrypt_body(session_key, iv_b64, data_b64):
    """AES-256-GCM 복호화 → 평문 bytes. data = ciphertext || tag(16B)."""
    iv = base64.b64decode(iv_b64)
    data = base64.b64decode(data_b64)
    return AESGCM(session_key).decrypt(iv, data, None)


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


# --- 앱 요청 서명 (HMAC-SHA256) -------------------------------------------

def _app_hmac_secret():
    """앱과 공유하는 HMAC 시크릿(bytes). 문자열을 UTF-8 로 인코딩해 키로 쓴다."""
    secret = getattr(settings, 'PAYLOAD_APP_HMAC_SECRET', '')
    if not secret:
        return b''
    if isinstance(secret, str):
        secret = secret.encode('utf-8')
    return secret


def sign_request(method, full_path, enc_key_b64):
    """앱과 동일 규약으로 서명 문자열을 만든 뒤 HMAC-SHA256 → base64.

    서명 대상: "METHOD\nFULL_PATH\nX-Enc-Key"
      - METHOD  : HTTP 메서드(대문자)
      - FULL_PATH: 쿼리스트링 포함 경로 (예: /api/v1/orders?page=2)
      - X-Enc-Key: RSA-OAEP 로 감싼 세션키 base64 (요청마다 랜덤이라 nonce 역할)

    본문 무결성은 AES-GCM tag 가 이미 보장하므로 서명은 본문을 포함하지 않는다.
    (본문을 바꾸면 GCM 복호화가 실패한다.)
    """
    msg = f'{method}\n{full_path}\n{enc_key_b64}'.encode('utf-8')
    mac = hmac.new(_app_hmac_secret(), msg, hashlib.sha256).digest()
    return base64.b64encode(mac).decode('ascii')


def verify_app_signature(method, full_path, enc_key_b64, sig_b64):
    """앱 서명 검증. 시크릿 미설정이거나 값이 비면 False(=거부)."""
    if not _app_hmac_secret() or not sig_b64 or not enc_key_b64:
        return False
    expected = sign_request(method, full_path, enc_key_b64)
    return constant_time_compare(expected, sig_b64)
