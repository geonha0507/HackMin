"""[방어 ④] 거래 서명(Txn Signature) 검증 — Keystore/TEE 비대칭 서명.

계좌 변경 같은 '민감한 돈 액션'은 1층 HMAC 외에 2층 서명을 요구한다.
앱이 Android Keystore(EC P-256)에서 만든 개인키로 아래 정규문(canonical)을
SHA256withECDSA 서명하고, 헤더로 붙인다:

    X-Txn-Ts     : 밀리초 타임스탬프
    X-Txn-Nonce  : 요청마다 1회성 난수
    X-Txn-Sig    : base64(ECDSA 서명)
    X-Key-Id     : 등록 시 서버가 저장한 공개키 식별자

canonical = "METHOD\\nPATH\\nTS\\nNONCE\\nBODY"

서버는 요청자(request.user)가 등록한 **공개키로만** 검증하므로, 개인키가 없는
커스텀 클라이언트는 유효 서명을 만들 수 없다. (오프라인 위조 불가)

주의: 서명은 '진짜 앱 인스턴스가 보냈다'만 증명한다. '그게 네 계좌냐'(소유권)는
별개의 검사다 — 계좌변경 엔드포인트에 소유권 검사가 없으면(IDOR), 공격자는 자기
키로 서명한 요청으로 남의 계좌를 바꿀 수 있다(=의도된 취약점).
"""
import base64
import binascii
import time

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.serialization import load_pem_public_key

# 타임스탬프 허용 오차(밀리초). 오래된 요청 캡처 재전송을 막는다.
TS_WINDOW_MS = 90_000


class TxnSigError(Exception):
    """거래 서명 검증 실패. 호출부에서 401 로 변환한다."""


def canonical(method, path, ts, nonce, body):
    """앱과 동일 규약의 서명 대상 바이트."""
    if isinstance(body, (bytes, bytearray)):
        body = bytes(body).decode('utf-8', 'replace')
    return '{}\n{}\n{}\n{}\n{}'.format(method, path, ts, nonce, body).encode('utf-8')


def verify_txn(user, method, path, ts_raw, nonce, sig_b64, key_id, body):
    """요청자(user)의 등록 공개키로 거래 서명을 검증한다. 성공 시 nonce 를 소모한다.

    실패하면 TxnSigError 를 던진다(=401). 성공하면 True.
    """
    from rider.models import TxnKey, TxnNonce

    try:
        ts = int(ts_raw)
    except (TypeError, ValueError):
        raise TxnSigError('타임스탬프 형식 오류')
    now = int(time.time() * 1000)
    if abs(now - ts) > TS_WINDOW_MS:
        raise TxnSigError('만료되었거나 미래 시각의 요청')
    if not nonce or not sig_b64 or not key_id:
        raise TxnSigError('서명 헤더 누락')

    # 1회성 nonce (재전송 방지)
    if TxnNonce.objects.filter(nonce=nonce).exists():
        raise TxnSigError('nonce 재사용')

    tk = TxnKey.objects.filter(user=user, key_id=key_id).first()
    if not tk:
        raise TxnSigError('등록된 서명 키 없음')

    try:
        sig = base64.b64decode(sig_b64, validate=True)
    except (binascii.Error, ValueError):
        raise TxnSigError('서명 base64 디코딩 실패')

    try:
        pub = load_pem_public_key(tk.public_key_pem.encode('utf-8'))
        pub.verify(sig, canonical(method, path, ts, nonce, body),
                   ec.ECDSA(hashes.SHA256()))
    except InvalidSignature:
        raise TxnSigError('서명 불일치')
    except Exception as exc:  # 키 파싱 오류 등
        raise TxnSigError('서명 검증 오류') from exc

    TxnNonce.objects.create(nonce=nonce)
    return True
