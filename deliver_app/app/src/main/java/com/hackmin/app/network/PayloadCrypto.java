package com.hackmin.app.network;

import android.util.Base64;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/**
 * 페이로드 하이브리드 암호화 헬퍼 (RSA-OAEP-SHA256 + AES-256-GCM).
 *
 * <p>서버 {@code apps/common/crypto.py} 와 파라미터가 정확히 일치해야 상호운용된다:
 * <ul>
 *   <li>RSA/ECB/OAEPWithSHA-256AndMGF1Padding (hash=SHA-256, MGF1=SHA-256)</li>
 *   <li>AES/GCM/NoPadding, 256bit key, 12-byte IV, 128-bit tag(ciphertext 뒤 부착)</li>
 * </ul>
 *
 * <p>여기에 담긴 것은 서버 <b>공개키</b>뿐이라 APK가 뜯겨도 복호화 비밀은 새지 않는다.
 * (요청·응답 세션키는 서버 개인키로만 복원 가능)
 */
public final class PayloadCrypto {

    /**
     * 서버 공개키 (SPKI DER, base64 단일 라인). keys/payload_public.pem 와 동일 키.
     * 데모용 개발 키이며, 서버 개인키 교체 시 이 값도 함께 갱신해야 한다.
     */
    private static final String SERVER_PUBLIC_KEY_B64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3wQvB1DS2sikNcI80xbp"
            + "RBKqYLiUGMi18cROunkoXYynpzXOcMKPfyEK9PvhInCzJ4VcTun3IUE+j4+tf3s+"
            + "uoiimg/u7L6ZC7rd0Yh7dIok1qd9IjJRpFFy77W+d0e9XRv6duEa+TxVLLrpflRn"
            + "34RiyZHejanHBhMDYw6QYs2WfdyQFhdhnYmc6cZxTuJjGVHMb2TCiPZqbfrh/49U"
            + "bsq1atlZ5u/k8j0hEeofTShv7i1CSPycEBo+hSCfUG78knHps1be6fmz5Fg+E4hN"
            + "A8S0srZC2Za27MREl22nKhh/dK3SiElhD2lgAA5vpikOj1SOiq4rJy+j+d/snDCd"
            + "wwIDAQAB";

    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LEN = 12;
    private static final SecureRandom RNG = new SecureRandom();

    private static volatile PublicKey serverKey;

    private PayloadCrypto() {}

    private static PublicKey serverKey() throws Exception {
        if (serverKey == null) {
            byte[] der = Base64.decode(SERVER_PUBLIC_KEY_B64, Base64.NO_WRAP);
            serverKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
        }
        return serverKey;
    }

    /** 요청마다 새로 만드는 AES-256 세션키. */
    public static byte[] newSessionKey() {
        byte[] k = new byte[32];
        RNG.nextBytes(k);
        return k;
    }

    public static byte[] randomIv() {
        byte[] iv = new byte[IV_LEN];
        RNG.nextBytes(iv);
        return iv;
    }

    /** 세션키를 서버 공개키로 RSA-OAEP(SHA-256/MGF1-SHA256) 암호화 → X-Enc-Key 값. */
    public static byte[] wrapSessionKey(byte[] sessionKey) throws Exception {
        Cipher c = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        // 프로바이더 기본 MGF1이 SHA-1인 경우가 있어, 서버(SHA-256)와 맞추도록 명시한다.
        OAEPParameterSpec spec = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        c.init(Cipher.ENCRYPT_MODE, serverKey(), spec);
        return c.doFinal(sessionKey);
    }

    /** AES-256-GCM 암호화 → ciphertext||tag(16B). */
    public static byte[] aesGcmEncrypt(byte[] key, byte[] iv, byte[] plaintext) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
        return c.doFinal(plaintext);
    }

    /** AES-256-GCM 복호화. data = ciphertext||tag(16B). */
    public static byte[] aesGcmDecrypt(byte[] key, byte[] iv, byte[] data) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
        return c.doFinal(data);
    }

    public static String b64(byte[] b) {
        return Base64.encodeToString(b, Base64.NO_WRAP);
    }

    public static byte[] unb64(String s) {
        return Base64.decode(s, Base64.NO_WRAP);
    }
}
