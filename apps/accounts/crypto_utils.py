"""AES-128 encryption/decryption utilities for sensitive data."""

import os
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