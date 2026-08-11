package com.hackmin.app.network;

import android.util.Base64;

import com.hackmin.app.BuildConfig;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/**
 * 페이로드 하이브리드 암호화 + 앱 요청 서명 헬퍼
 * (RSA-OAEP-SHA256 + AES-256-GCM + HMAC-SHA256).
 *
 * <p>서버 {@code apps/common/crypto.py} 와 파라미터가 정확히 일치해야 상호운용된다:
 * <ul>
 *   <li>RSA/ECB/OAEPWithSHA-256AndMGF1Padding (hash=SHA-256, MGF1=SHA-256)</li>
 *   <li>AES/GCM/NoPadding, 256bit key, 12-byte IV, 128-bit tag(ciphertext 뒤 부착)</li>
 *   <li>HMAC-SHA256(secret, "METHOD\nFULL_PATH\nX-Enc-Key\nBODY_SHA256") → base64</li>
 *   <li>BODY_SHA256 = base64(sha256(네트워크로 나가는 원본 본문 바이트))</li>
 * </ul>
 *
 * <p><b>비밀의 위치</b>가 이 방식의 핵심이다:
 * <ul>
 *   <li>서버 <b>공개키</b>만 담긴다 → APK 가 뜯겨도 복호화 비밀은 새지 않는다.
 *       (세션키는 서버 개인키로만 복원 가능)</li>
 *   <li>HMAC 시크릿은 <b>이 바이너리에만</b> 존재해야 한다 → 소스에 상수로 박지 않고
 *       {@link BuildConfig} 를 통해 빌드 시점에 주입한다(gradle.properties 는 gitignore).
 *       서버는 이 서명으로 "정말 앱이 보낸 요청"인지 검증한다.</li>
 * </ul>
 *
 * <p><b>gradle 설정</b> (app/build.gradle):
 * <pre>
 * android {
 *     buildFeatures { buildConfig true }
 *     defaultConfig {
 *         buildConfigField "String", "PAYLOAD_HMAC_SECRET",
 *                 "\"${project.findProperty('payloadHmacSecret') ?: ''}\""
 *     }
 * }
 * </pre>
 * 그리고 <b>gitignore 된</b> gradle.properties 에:
 * <pre>payloadHmacSecret=서버 PAYLOAD_APP_HMAC_SECRET 와 동일한 값</pre>
 */
public final class PayloadCrypto {

    /**
     * 서버 공개키 (SPKI DER, base64 단일 라인). keys/payload_public.pem 와 동일 키.
     * 공개키이므로 리포에 있어도 무방하다. 서버 개인키 교체 시 함께 갱신할 것.
     */
    private static final String SERVER_PUBLIC_KEY_B64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1IZA+tLtDFM2hcwJBdha"
            + "vVcYahb4FtCRRNfO8X2F3fTx90a6JBkNmOCiLczKwAISSJASkBjzYVhdamsl39hy"
            + "eEHHSbWWak899l6yCfqSkP3u4+UG/HGw6aVx3UrT5hjUN+XtnymQdmMPTQZvmA/5"
            + "0prx3l/uTU0vmRcxRGRvllf7562b8p9Fw6fy5T3L5kufj/lZDoi8/y0lUnPaHWUO"
            + "3ph/hFyHZ2IVKHH+6nVPJ/LCcBMMrFYvIsgaG0UNaKQn8jAIFKjumKVAGQklzeMq"
            + "bRa+am+JVzNTJZqp/q9EgIq0bymZ822U9ucO36zVhvgx4Qdhbj13xrnnT5tY8a3P"
            + "ZQIDAQAB";

    /**
     * 앱 요청 서명용 HMAC 시크릿. 서버 settings.PAYLOAD_APP_HMAC_SECRET 와 동일해야 한다.
     * <b>소스에 하드코딩하지 않는다</b> — 빌드 시점에 BuildConfig 로 주입된다.
     */
    private static final String APP_HMAC_SECRET = BuildConfig.PAYLOAD_HMAC_SECRET;

    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LEN = 12;
    private static final SecureRandom RNG = new SecureRandom();

    private static volatile PublicKey serverKey;

    private PayloadCrypto() {}

    /** HMAC 시크릿이 주입됐는지. 비어 있으면 서명을 만들 수 없다(= 서버가 거부). */
    public static boolean hasSecret() {
        return APP_HMAC_SECRET != null && !APP_HMAC_SECRET.isEmpty();
    }

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

    /** 서버 crypto.body_digest 와 동일: base64(SHA-256(bytes)). */
    public static String sha256B64(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return b64(md.digest(data == null ? new byte[0] : data));
    }

    /**
     * 요청 서명. 서버 crypto.sign_request 와 동일 규약:
     * {@code HMAC-SHA256(secret, "METHOD\nFULL_PATH\nX-Enc-Key\nBODY_SHA256")} → base64.
     *
     * @param method       HTTP 메서드(대문자, 예: "POST")
     * @param fullPath     쿼리 포함 경로 (예: "/api/v1/orders?page=2")
     * @param encKeyB64    X-Enc-Key 헤더 값(래핑된 세션키 base64)
     * @param bodySha256B64 X-Body-Sha256 헤더 값(원본 본문 바이트의 base64 SHA-256)
     */
    public static String signRequest(String method, String fullPath,
                                     String encKeyB64, String bodySha256B64) throws Exception {
        String message = method + "\n" + fullPath + "\n" + encKeyB64 + "\n" + bodySha256B64;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                APP_HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return b64(sig);
    }

    public static String b64(byte[] b) {
        return Base64.encodeToString(b, Base64.NO_WRAP);
    }

    public static byte[] unb64(String s) {
        return Base64.decode(s, Base64.NO_WRAP);
    }
}
