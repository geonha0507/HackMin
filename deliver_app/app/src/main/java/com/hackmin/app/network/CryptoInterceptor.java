package com.hackmin.app.network;

import android.util.Log;

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
import okio.BufferedSink;
import okio.HashingSink;
import okio.Okio;

/**
 * 요청/응답 본문을 하이브리드 암호화하고, 요청에 HMAC 서명을 붙이는 OkHttp 인터셉터.
 *
 * <p>요청마다 AES-256 세션키를 만들어 본문을 GCM으로 암호화하고, 세션키를 서버 공개키로
 * RSA-OAEP 암호화해 {@code X-Enc-Key} 헤더로 보낸다. 서버는 같은 세션키로 응답을 암호화하며,
 * 이 인터셉터가 응답을 복호화해 평문 JSON으로 Retrofit에 넘긴다.
 *
 * <p>서명({@code X-Sig})은 <b>본문 해시까지 묶는다</b>:
 * {@code HMAC(secret, "METHOD\nFULL_PATH\nX-Enc-Key\nBODY_SHA256")}.
 * 본문을 서명에서 빼면 캡처한 서명을 재사용해 임의 본문을 밀어 넣을 수 있어서,
 * multipart 처럼 평문으로 나가는 본문의 무결성이 무너진다.
 *
 * <p><b>인터셉터 체인에서 반드시 마지막(소켓에 가장 가깝게)에 등록</b>해야 소켓으로 나가는
 * 바이트가 암호문이 되어 Burp 등 프록시엔 암호문만 잡힌다.
 *
 * <p>multipart(파일 업로드) 본문은 암호화하지 않지만 <b>해시가 서명에 포함</b>되므로 변조는
 * 불가능하다. 응답은 {@code X-Enc:1} 표시가 있을 때만 복호화한다(이미지/다운로드 등은 무시).
 *
 * <p>암호화에 실패하면 평문으로 폴백하지 않고 {@link IOException} 을 던진다.
 * (폴백하면 평문이 그대로 소켓에 나가 암호화의 의미가 사라진다 — fail-closed & fail-loud)
 * 단 HMAC 시크릿이 주입되지 않은 빌드는 {@code X-Sig} 없이 전송한다. 듀얼 모드
 * (서버 {@code PAYLOAD_ENFORCE=0})에서는 서명을 검사하지 않아 통신이 되기 때문이다.
 */
public class CryptoInterceptor implements Interceptor {

    /** 암호화 on/off. Burp A/B 테스트 시 false 로 두고 재빌드하면 평문 통신이 된다. */
    public static final boolean ENABLED = true;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String H_ENC_KEY = "X-Enc-Key";
    private static final String H_ENC = "X-Enc";
    private static final String H_SIG = "X-Sig";
    private static final String H_BODY_SHA = "X-Body-Sha256";

    private static volatile boolean warnedNoSecret = false;

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        if (!ENABLED) {
            return chain.proceed(original);
        }
        byte[] sessionKey = PayloadCrypto.newSessionKey();
        Request.Builder rb = original.newBuilder();

        try {
            // 1) 본문을 먼저 확정한다. JSON 이면 봉투로 치환, 그 외(multipart 등)는 원본 유지.
            //    서명이 본문 해시를 포함하므로 '실제로 소켓에 나갈 바이트'를 기준으로 해싱해야 한다.
            RequestBody body = original.body();
            String bodySha;

            if (body == null || body.contentLength() == 0) {
                bodySha = PayloadCrypto.sha256B64(new byte[0]);
            } else if (isJson(body.contentType())) {
                Buffer buf = new Buffer();
                body.writeTo(buf);
                byte[] plain = buf.readByteArray();
                byte[] iv = PayloadCrypto.randomIv();
                byte[] ct = PayloadCrypto.aesGcmEncrypt(sessionKey, iv, plain);
                String envelope = new JSONObject()
                        .put("iv", PayloadCrypto.b64(iv))
                        .put("data", PayloadCrypto.b64(ct))
                        .toString();
                byte[] envelopeBytes = envelope.getBytes("UTF-8");
                bodySha = PayloadCrypto.sha256B64(envelopeBytes);
                rb.method(original.method(), RequestBody.create(envelopeBytes, JSON));
            } else {
                // multipart 등: 평문 그대로 보내되 전체 바이트를 스트리밍 해싱한다.
                // (메모리에 통째로 올리지 않으려고 HashingSink 를 쓴다)
                bodySha = PayloadCrypto.b64(hashBody(body));
            }

            // 2) 세션키를 서버 공개키로 감싸고, 메서드·경로·본문해시까지 묶어 서명한다.
            String encKeyB64 = PayloadCrypto.b64(PayloadCrypto.wrapSessionKey(sessionKey));
            String sig = null;
            if (PayloadCrypto.hasSecret()) {
                sig = PayloadCrypto.signRequest(
                        original.method(), fullPath(original.url()), encKeyB64, bodySha);
            } else if (!warnedNoSecret) {
                // 듀얼 모드(서버 PAYLOAD_ENFORCE=0)에서는 서버가 서명을 검사하지 않으므로
                // 시크릿 없이도 통신은 된다. 강제 모드에서는 이 요청이 400 으로 거부된다.
                warnedNoSecret = true;
                Log.w("CryptoInterceptor",
                        "PAYLOAD_HMAC_SECRET 미주입 — X-Sig 없이 전송합니다. "
                        + "서버가 강제 모드면 400 이 됩니다. gradle.properties 를 확인하세요.");
            }

            rb.header(H_ENC_KEY, encKeyB64);
            rb.header(H_BODY_SHA, bodySha);
            if (sig != null) {
                rb.header(H_SIG, sig);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("요청 암호화/서명 실패", e);
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

    /** 본문 전체를 메모리에 올리지 않고 SHA-256 해시만 계산한다. */
    private static byte[] hashBody(RequestBody body) throws IOException {
        HashingSink sink = HashingSink.sha256(Okio.blackhole());
        BufferedSink buffered = Okio.buffer(sink);
        body.writeTo(buffered);
        buffered.flush();
        return sink.hash().toByteArray();
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
