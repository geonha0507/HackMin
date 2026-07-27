"""AES-128 encryption/decryption utilities for sensitive data."""

import os
import hashlib
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad, unpad
import base64

_OBFUSCATED_KEY = "SGFja01pblNlY3JldGtleQ=="

def _get_cipher_key():
    """Get 16-byte AES-128 key."""
    key = base64.b64decode(_OBFUSCATED_KEY)
    if isinstance(key, str):
        key = key.encode()
    if len(key) != 16:
        raise ValueError(f'AES key must be 16 bytes, got {len(key)} bytes')
    return key


def encrypt_aes128(plaintext):
    """Encrypt plaintext using AES-128-CBC with PKCS7 padding.

    Returns: base64-encoded string of IV+ciphertext
    """
    if isinstance(plaintext, str):
        plaintext = plaintext.encode('utf-8')

    key = _get_cipher_key()
    iv = os.urandom(16)
    cipher = AES.new(key, AES.MODE_CBC, iv)
    padded = pad(plaintext, AES.block_size)
    ciphertext = cipher.encrypt(padded)

    # Return IV + ciphertext as base64
    return base64.b64encode(iv + ciphertext).decode('utf-8')


def decrypt_aes128(encrypted_text):
    """Decrypt base64-encoded encrypted text.

    Args:
        encrypted_text: base64-encoded string of IV+ciphertext

    Returns: plaintext string
    """
    if isinstance(encrypted_text, str):
        encrypted_text = encrypted_text.encode('utf-8')

    key = _get_cipher_key()
    encrypted_data = base64.b64decode(encrypted_text)

    iv = encrypted_data[:16]
    ciphertext = encrypted_data[16:]

    cipher = AES.new(key, AES.MODE_CBC, iv)
    padded_plaintext = cipher.decrypt(ciphertext)
    plaintext = unpad(padded_plaintext, AES.block_size)

    return plaintext.decode('utf-8')

# ── AES-256 (결제수단 등 민감정보용) ──────────────────────────────
# 카드번호/계좌번호는 AES-256-CBC로 암호화해 저장한다. 화면 표시는 마스킹값을 별도로 저장한다.
_AES256_PASSPHRASE = "HackMinPaymentAES256SecretKey"


def _get_aes256_key():
    """32바이트(AES-256) 키. 패스프레이즈를 SHA-256으로 해시해 항상 32바이트를 보장한다."""
    return hashlib.sha256(_AES256_PASSPHRASE.encode('utf-8')).digest()


def encrypt_aes256(plaintext):
    """AES-256-CBC + PKCS7 패딩으로 암호화. 반환: base64(IV+ciphertext)."""
    if isinstance(plaintext, str):
        plaintext = plaintext.encode('utf-8')
    key = _get_aes256_key()
    iv = os.urandom(16)
    cipher = AES.new(key, AES.MODE_CBC, iv)
    ciphertext = cipher.encrypt(pad(plaintext, AES.block_size))
    return base64.b64encode(iv + ciphertext).decode('utf-8')


def decrypt_aes256(encrypted_text):
    """base64(IV+ciphertext)를 복호화해 평문 문자열을 반환한다."""
    if isinstance(encrypted_text, str):
        encrypted_text = encrypted_text.encode('utf-8')
    key = _get_aes256_key()
    data = base64.b64decode(encrypted_text)
    iv, ciphertext = data[:16], data[16:]
    cipher = AES.new(key, AES.MODE_CBC, iv)
    return unpad(cipher.decrypt(ciphertext), AES.block_size).decode('utf-8')
