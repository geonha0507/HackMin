"""페이로드 하이브리드 암호화 헬퍼 (RSA-OAEP-SHA256 + AES-256-GCM).

흐름:
  - 앱이 요청마다 임의의 AES-256 세션키 K를 만들어 본문을 AES-GCM으로 암호화하고,
    K를 서버 공개키로 RSA-OAEP 암호화해 X-Enc-Key 헤더로 보낸다.
  - 서버는 개인키로 K를 복원(unwrap)해 요청 본문을 복호화하고,
    같은 K로 응답을 암호화한다.

Burp 같은 네트워크 프록시는 서버 개인키가 없어 K를 복원할 수 없으므로
요청·응답 본문이 모두 암호문으로만 보인다(전송계층 TLS 유무와 무관).

앱(Java)과 파라미터를 정확히 맞춰야 상호운용된다:
  - RSA/ECB/OAEPWithSHA-256AndMGF1Padding  (hash=SHA-256, MGF1=SHA-256)
  - AES/GCM/NoPadding, 256bit key, 12-byte IV, 128-bit tag(ct 뒤에 부착)
"""
import base64
import os

from django.conf import settings

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
