package com.hackmin.app.security;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 앱-서버 간 종단 페이로드 암호화(E2E) 유틸.
 * HTTPS(전송 구간 암호화)와는 별개로, 요청 바디(비밀번호 등)를
 * 애플리케이션 레이어에서 한 번 더 AES-GCM으로 암호화한다.
 *
 * 목적: Burp 같은 MITM 프록시가 클라이언트 CA 인증서를 신뢰하도록 설정해
 *      TLS를 벗겨내더라도, 프록시 화면에는 암호문만 보이도록 하기 위함.
 *      평문 확인은 런타임에 이 encrypt() 호출부를 Frida로 후킹해야만 가능하게 만든다.
 */
public class CryptoUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    // ⚠️ 데모/과제용 고정 키 (32바이트 = AES-256).
    // 실제 서비스에서는 이렇게 하드코딩하면 안 되고, 아래 "한계" 설명 참고.
    private static final byte[] SECRET_KEY =
            "hackmin-2026-secret-key-32byte!".getBytes(StandardCharsets.UTF_8);

    /** 평문 -> Base64(IV + 암호문) */
    public static String encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(SECRET_KEY, "AES");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // 서버에서 앞 12바이트를 IV로 분리해서 복호화하는 구조
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException("암호화 실패", e);
        }
    }

    /** Base64(IV + 암호문) -> 평문 (서버 응답도 암호화해서 줄 경우 클라이언트에서 복호화용) */
    public static String decrypt(String base64CipherText) {
        try {
            byte[] combined = Base64.decode(base64CipherText, Base64.NO_WRAP);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(SECRET_KEY, "AES");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);

            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("복호화 실패", e);
        }
    }
}