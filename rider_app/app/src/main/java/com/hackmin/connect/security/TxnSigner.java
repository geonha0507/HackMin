package com.hackmin.connect.security;

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
 * [방어 ④] 거래 서명기 — Android Keystore(EC P-256, StrongBox→TEE) 하드웨어 키.
 *
 * <p>계좌 변경 같은 '돈을 움직이는' 요청에 붙이는 2층 서명을 만든다. 개인키는
 * Keystore가 보안 하드웨어 안에서 생성·보관하며 <b>앱조차 원시 키 바이트를 볼 수 없다</b>.
 * 서버는 등록된 공개키로만 서명을 검증하므로, 개인키가 없는 커스텀 클라이언트는
 * 유효 서명을 만들 수 없다(오프라인 위조 불가).
 *
 * <p><b>보안 한계(교육 포인트)</b>: 개인키의 '추출'은 막지만, 루팅된 기기에서
 * 살아있는 앱의 {@link #sign(byte[])} 을 Frida로 호출하는 '서명 오라클' 오용은
 * 막지 못한다 → 그래서 계좌변경(대량 탈취)의 유일한 우회 경로가 '라이더앱 루팅'이 된다.
 */
public final class TxnSigner {

    private static final String ALIAS = "hackmin_txn_key";
    private static final String KEYSTORE = "AndroidKeyStore";

    private TxnSigner() {}

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

    /**
     * 돈 액션 요청에만 호출. 정규문(canonical) 바이트를 SHA256withECDSA 로 서명 → base64.
     * canonical = "METHOD\nPATH\nTS\nNONCE\nBODY" (서버 common/txnsig.py 와 동일 규약).
     *
     * <p>실제 서명 연산은 TEE 내부에서 일어나고, 개인키는 밖으로 나오지 않는다.
     * (Frida 오라클은 이 함수를 '내 바이트'로 호출해 진짜 서명을 얻어낸다.)
     */
    public static String sign(byte[] canonical) throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        PrivateKey priv = (PrivateKey) ks.getKey(ALIAS, null);
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(priv);
        s.update(canonical);
        return Base64.encodeToString(s.sign(), Base64.NO_WRAP);
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
