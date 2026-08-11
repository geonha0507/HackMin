package com.hackmin.app.network;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

/**
 * 요청/응답 본문을 하이브리드 암호화하고, 요청에 HMAC 서명을 붙이는 OkHttp 인터셉터.
 *
 * <p>요청마다 AES-256 세션키를 만들어 본문을 GCM으로 암호화하고, 세션키를 서버 공개키로
 * RSA-OAEP 암호화해 {@code X-Enc-Key} 헤더로 보낸다. 서버는 같은 세션키로 응답을 암호화하며,
 * 이 인터셉터가 응답을 복호화해 평문 JSON으로 Retrofit에 넘긴다.
 *
 * <p>또한 {@code X-Sig} 헤더에 HMAC-SHA256 서명을 실어 "정말 앱이 보낸 요청"임을 증명한다.
 * 서버는 서명이 없거나 틀리면 400 으로 거부한다. 공개키는 누구나 알 수 있어 봉투 자체는
 * 위조 가능하지만, HMAC 시크릿({@link PayloadCrypto} 안)이 없으면 유효 서명을 만들 수 없다.
 *
 * <p><b>인터셉터 체인에서 반드시 마지막(소켓에 가장 가깝게)에 등록</b>해야 소켓으로 나가는
 * 바이트가 암호문이 되어 Burp 등 프록시엔 암호문만 잡힌다.
 *
 * <p>비암호화 대상은 그대로 통과한다: multipart(파일 업로드) 본문은 암호화하지 않으며(단
 * 서명 헤더는 붙는다), 응답도 {@code X-Enc:1} 표시가 있을 때만 복호화한다(이미지/다운로드
 * 등은 무시).
 */
public class CryptoInterceptor implements Interceptor {

    /** 암호화 on/off. Burp A/B 테스트 시 false 로 두고 재빌드하면 평문 통신이 된다. */
    public static final boolean ENABLED = true;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String H_ENC_KEY = "X-Enc-Key";
    private static final String H_ENC = "X-Enc";
    private static final String H_SIG = "X-Sig";

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        if (!ENABLED) {
            return chain.proceed(original);
        }

        byte[] sessionKey = PayloadCrypto.newSessionKey();

        // 세션키를 서버 공개키로 감싸고, 그 값에 메서드·경로를 묶어 HMAC 서명한다.
        // (서버 검증 대상: "METHOD\nFULL_PATH\nX-Enc-Key")
        Request.Builder rb = original.newBuilder();
        try {
            String encKeyB64 = PayloadCrypto.b64(PayloadCrypto.wrapSessionKey(sessionKey));
            String sig = PayloadCrypto.signRequest(
                    original.method(), fullPath(original.url()), encKeyB64);
            rb.header(H_ENC_KEY, encKeyB64);
            rb.header(H_ENC, "1");
            rb.header(H_SIG, sig);
        } catch (Exception e) {
            // 암호화/서명 준비 실패 → 평문으로 폴백(통신은 되게). 단 서버가 강제 모드면
            // 서명 없는 이 요청은 400 으로 거부된다(fail-closed).
            return chain.proceed(original);
        }

        // 요청 본문이 JSON이면 봉투로 치환. (multipart/빈 본문은 그대로 두되 헤더는 유지)
        RequestBody body = original.body();
        if (body != null && isJson(body.contentType())) {
            try {
                Buffer buf = new Buffer();
                body.writeTo(buf);
                byte[] plain = buf.readByteArray();
                byte[] iv = PayloadCrypto.randomIv();
                byte[] ct = PayloadCrypto.aesGcmEncrypt(sessionKey, iv, plain);
                String envelope = new JSONObject()
                        .put("iv", PayloadCrypto.b64(iv))
                        .put("data", PayloadCrypto.b64(ct))
                        .toString();
                rb.method(original.method(), RequestBody.create(envelope, JSON));
            } catch (Exception e) {
                return chain.proceed(original); // 암호화 실패 → 평문 폴백
            }
        }

        Response resp = chain.proceed(rb.build());

        // 응답이 암호화됐으면(X-Enc:1) 복호화해 평문 JSON으로 바꿔 Retrofit에 전달.
        if ("1".equals(resp.header(H_ENC)) && resp.body() != null) {
            String cipherText = resp.body().string(); // body 소비됨 → 아래서 재구성
            try {
                JSONObject env = new JSONObject(cipherText);
                byte[] iv = PayloadCrypto.unb64(env.getString("iv"));
                byte[] data = PayloadCrypto.unb64(env.getString("data"));
                byte[] plain = PayloadCrypto.aesGcmDecrypt(sessionKey, iv, data);
                ResponseBody newBody = ResponseBody.create(plain, JSON);
                return resp.newBuilder().body(newBody).removeHeader(H_ENC).build();
            } catch (Exception e) {
                throw new IOException("응답 복호화 실패", e);
            }
        }
        return resp;
    }

    /** 서버 request.get_full_path() 와 동일한 "경로+?쿼리" 문자열. */
    private static String fullPath(HttpUrl url) {
        String path = url.encodedPath();
        String query = url.encodedQuery();
        return query != null ? path + "?" + query : path;
    }

    private static boolean isJson(MediaType type) {
        return type != null && "application".equals(type.type())
                && type.subtype() != null && type.subtype().contains("json");
    }
}
