package com.hackmin.app.security;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

/**
 * [방어 ⑩] 수령확인 거래 서명기 — 네이티브(libhackminsec.so) 진입 + Android Keystore(EC P-256) 하드웨어 키.
 *
 * <p>수령확인 요청에 붙이는 서명을 만든다. 실제 서명 연산은 Keystore 하드웨어 키로 이뤄지고
 * 개인키는 TEE 밖으로 나오지 않는다(오프라인 위조 불가). 서명 진입점은 <b>네이티브 함수</b>
 * {@link #signReceipt(byte[])} 이며, 이 함수가 JNI 로 {@link #keystoreSign(byte[])} 를 호출한다.
 *
 * <p><b>보안 한계(교육 포인트)</b>: 개인키 '추출'은 막지만, 루팅 기기에서 살아있는
 * signReceipt() 를 Frida 로 후킹해 '서명할 데이터(canonical)'를 바꿔치기하는 '서명 오라클'
 * 오용은 막지 못한다 → 수령확인 위조의 유일한 경로가 '고객앱 루팅 + 네이티브 후킹'이 된다.
 */
public final class ReceiptSigner {

    private static final String ALIAS = "hackmin_receipt_key";
    private static final String KEYSTORE = "AndroidKeyStore";

    static {
        // 네이티브 서명 함수(signReceipt)가 이 .so 안에 있다. (루팅탐지 가드와 동일 lib)
        System.loadLibrary("hackminsec");
    }

    private ReceiptSigner() {}

    /**
     * 수령확인 서명 진입점(네이티브). canonical 바이트를 서명해 raw 서명 바이트를 돌려준다.
     * 실제 서명은 이 함수가 JNI 로 호출하는 {@link #keystoreSign(byte[])} 가 Keystore 로 수행한다.
     * (Frida 오라클은 이 함수를 후킹해 canonical 을 바꿔치기한다 → 후킹 타겟)
     */
    public static native byte[] signReceipt(byte[] canonical);

    /**
     * 네이티브 signReceipt() 가 JNI 로 호출한다. Keystore(EC P-256) 개인키로 SHA256withECDSA 서명.
     * 개인키는 TEE 내부에 있으며, 서명 연산만 하드웨어 안에서 수행된다(개인키 미노출).
     */
    public static byte[] keystoreSign(byte[] canonical) throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        PrivateKey priv = (PrivateKey) ks.getKey(ALIAS, null);
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(priv);
        s.update(canonical);
        return s.sign();
    }

    /**
     * canonical 을 서명해 base64 문자열로 돌려준다(헤더 X-Receipt-Sig 용).
     * 내부적으로 네이티브 signReceipt() → Keystore 서명 → base64.
     */
    public static String signB64(byte[] canonical) {
        try {
            byte[] sig = signReceipt(canonical);
            return sig == null ? null : Base64.encodeToString(sig, Base64.NO_WRAP);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 최초 1회 개인키를 보안 하드웨어에 생성하고, 등록용 공개키(PEM)를 돌려준다. */
    public static String ensurePublicKeyPem() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            return toPem(ks.getCertificate(ALIAS).getPublicKey());
        }
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE);
        try {
            kpg.initialize(spec(true).build());      // StrongBox(보안칩) 우선
            return toPem(kpg.generateKeyPair().getPublic());
        } catch (Exception strongBoxUnavailable) {
            kpg.initialize(spec(false).build());     // 없으면 TEE 폴백
            return toPem(kpg.generateKeyPair().getPublic());
        }
    }

    /** 서버에 등록/서명 헤더로 쓸 key_id (기기별 고정 별칭). */
    public static String keyId() {
        return ALIAS;
    }

    private static KeyGenParameterSpec.Builder spec(boolean strongBox) {
        KeyGenParameterSpec.Builder b = new KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256);
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            b.setIsStrongBoxBacked(true);
        }
        return b;
    }

    /** X.509 SubjectPublicKeyInfo(DER) → PEM. 서버는 PEM 을 파싱해 저장한다. */
    private static String toPem(PublicKey pub) {
        String b64 = Base64.encodeToString(pub.getEncoded(), Base64.NO_WRAP);
        StringBuilder sb = new StringBuilder("-----BEGIN PUBLIC KEY-----\n");
        for (int i = 0; i < b64.length(); i += 64) {
            sb.append(b64, i, Math.min(i + 64, b64.length())).append('\n');
        }
        return sb.append("-----END PUBLIC KEY-----\n").toString();
    }
}
