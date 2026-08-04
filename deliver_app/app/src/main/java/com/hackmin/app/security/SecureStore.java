package com.hackmin.app.security;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Android Keystore 기반 문자열 암복호화 유틸리티.
 *
 * <p>토큰 등 민감 문자열을 SharedPreferences에 평문으로 두지 않기 위해 사용한다.
 * 키는 AndroidKeyStore 안에서만 생성·보관되며 앱 프로세스 밖으로 나오지 않는다
 * (하드웨어 백드 키스토어면 TEE/StrongBox에 상주). 따라서 루팅 단말에서
 * {@code shared_prefs/HackminPrefs.xml}를 그대로 덤프해도 암호문만 노출된다.</p>
 *
 * <p>AES-256/GCM을 쓴다. {@code EncryptedSharedPreferences}(androidx.security)는
 * 2025-04 deprecated 되었으므로 Keystore를 직접 사용한다.</p>
 *
 * <p>저장 포맷: {@code Base64( IV(12B) || ciphertext+GCM tag )} — IV는 매 암호화마다
 * 새로 생성해 앞에 붙인다.</p>
 */
public final class SecureStore {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "hackmin_session_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;      // GCM 권장 nonce 길이(byte)
    private static final int GCM_TAG_BITS = 128;

    private SecureStore() {}

    /** 평문을 AES-256/GCM으로 암호화해 Base64 문자열로 반환한다. 빈 값은 그대로 통과. */
    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext == null ? "" : plaintext;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());

            byte[] iv = cipher.getIV();  // AndroidKeyStore가 안전한 IV를 생성해준다.
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception e) {
            // 암호화 실패 시 평문을 흘리지 않는다. 빈 값으로 저장.
            return "";
        }
    }

    /** {@link #encrypt(String)} 결과를 복호화한다. 평문이거나 복호화 불가면 빈 문자열. */
    public static String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) {
            return "";
        }
        try {
            byte[] all = Base64.decode(stored, Base64.NO_WRAP);
            if (all.length <= IV_LENGTH) {
                return "";
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] ct = new byte[all.length - IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);
            System.arraycopy(all, IV_LENGTH, ct, 0, ct.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /** 키스토어에서 세션 키를 가져오거나, 없으면 새로 생성한다. */
    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);

        KeyStore.Entry entry = ks.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }

        KeyGenerator kg = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // 잠금화면 없이도 앱이 백그라운드에서 토큰을 갱신할 수 있어야 하므로
                // 사용자 인증 요구는 걸지 않는다(키 보관 자체는 키스토어가 담당).
                .build();
        kg.init(spec);
        return kg.generateKey();
    }
}
