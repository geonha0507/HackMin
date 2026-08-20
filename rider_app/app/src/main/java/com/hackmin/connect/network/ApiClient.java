package com.hackmin.connect.network;

import android.content.Context;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.File;

import com.hackmin.connect.BuildConfig;
import com.hackmin.connect.data.api.AuthApi;
import com.hackmin.connect.data.api.RiderApi;
import com.hackmin.connect.data.api.UserApi;

import java.util.concurrent.TimeUnit;

/**
 * Central Retrofit setup — 해킹커넥트(라이더) 전용.
 *
 * <p>해킹의 민족(deliver_app)과 같은 백엔드를 쓴다. 라이더 앱은 인증(/auth),
 * 내 정보(/me), 배달(/rider/deliveries)만 사용하므로 그 세 서비스만 노출한다.</p>
 */
public final class ApiClient {

    private static final String BASE_URL = "https://hackmin.com/api/v1/";

    // GET 응답을 짧게(초 단위) 캐시한다. 변경 요청(PUT 등)이 성공하면 캐시를 전부
    // 비워 최신 데이터를 보장한다. (CryptoInterceptor가 켜져 있으면 캐시는 비활성)
    private static final int GET_CACHE_SECONDS = 20;

    private static Retrofit retrofit;
    private static Cache httpCache;

    private ApiClient() {}

    /**
     * 이미지 등 미디어 절대경로 구성을 위한 서버 오리진(scheme+host+port).
     * BASE_URL에서 "/api/..." 앞부분만 잘라낸다. 서버가 상대경로(/media/...)를 줄 때
     * 이 값을 앞에 붙여 절대 URL을 만든다(ImageLoader에서 사용).
     */
    public static String mediaBaseUrl() {
        int idx = BASE_URL.indexOf("/api/");
        return idx > 0 ? BASE_URL.substring(0, idx) : BASE_URL;
    }

    public static synchronized Retrofit getRetrofit(
            AuthInterceptor.TokenProvider tokenProvider, File cacheDir
    ) {
        if (retrofit == null) {
            // 요청/응답 본문에는 비밀번호·JWT가 그대로 들어간다. 릴리즈 빌드에서는
            // logcat으로 새지 않도록 로깅을 완전히 끈다(디버그 빌드만 BODY).
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(BuildConfig.DEBUG
                    ? HttpLoggingInterceptor.Level.BODY
                    : HttpLoggingInterceptor.Level.NONE);

            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    // 변경 요청(비-GET)이 성공하면 캐시 전체 무효화 → 상태 변경 후 항상 최신.
                    .addInterceptor(chain -> {
                        okhttp3.Request req = chain.request();
                        Response resp = chain.proceed(req);
                        if (!"GET".equals(req.method()) && resp.isSuccessful() && httpCache != null) {
                            try {
                                httpCache.evictAll();
                            } catch (Exception ignored) {
                            }
                        }
                        return resp;
                    })
                    .addInterceptor(new AuthInterceptor(tokenProvider))
                    .addInterceptor(logging)
                    // CryptoInterceptor는 반드시 application 인터셉터 중 마지막(소켓에 가장
                    // 가깝게) — 소켓으로 나가는 바이트가 암호문이 되어 Burp 등 프록시엔
                    // 암호문만 잡힌다. (deliver_app과 동일 구성)
                    .addInterceptor(new CryptoInterceptor())
                    // GET 응답에 짧은 캐시 허용(서버가 캐시 헤더를 안 줘도 강제로 붙인다).
                    .addNetworkInterceptor(chain -> {
                        Response resp = chain.proceed(chain.request());
                        if ("GET".equals(chain.request().method()) && resp.isSuccessful()) {
                            return resp.newBuilder()
                                    .removeHeader("Pragma")
                                    .removeHeader("Cache-Control")
                                    .header("Cache-Control", "public, max-age=" + GET_CACHE_SECONDS)
                                    .build();
                        }
                        return resp;
                    })
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS);

            // 페이로드 암호화가 켜지면 HTTP 응답 캐시를 끈다. 세션키가 요청마다 랜덤이라
            // 캐시된 암호문은 복호화할 수 없기 때문(=캐시 무의미). deliver_app 참고.
            if (cacheDir != null && !CryptoInterceptor.ENABLED) {
                httpCache = new Cache(new File(cacheDir, "http-cache"), 5L * 1024 * 1024); // 5MB
                builder.cache(httpCache);
            }

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(builder.build())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    /** 임의의 Retrofit 서비스를 SessionManager 기반으로 생성한다. */
    public static <T> T api(Context context, Class<T> service) {
        SessionManager session = SessionManager.getInstance(context);
        return getRetrofit(session, context.getCacheDir()).create(service);
    }

    public static AuthApi authApi(Context context) { return api(context, AuthApi.class); }

    public static UserApi userApi(Context context) { return api(context, UserApi.class); }

    public static RiderApi riderApi(Context context) { return api(context, RiderApi.class); }
}
