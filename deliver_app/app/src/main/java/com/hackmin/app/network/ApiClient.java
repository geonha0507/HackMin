package com.hackmin.app.network;

import android.content.Context;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.File;

import com.hackmin.app.data.api.AdminApi;
import com.hackmin.app.data.api.AuthApi;
import com.hackmin.app.data.api.CartApi;
import com.hackmin.app.data.api.ChatbotApi;
import com.hackmin.app.data.api.DownloadApi;
import com.hackmin.app.data.api.InquiryApi;
import com.hackmin.app.data.api.LocationApi;
import com.hackmin.app.data.api.NoticeApi;
import com.hackmin.app.data.api.OrderApi;
import com.hackmin.app.data.api.OwnerApi;
import com.hackmin.app.data.api.PaymentApi;
import com.hackmin.app.data.api.PromotionApi;
import com.hackmin.app.data.api.RestaurantApi;
import com.hackmin.app.data.api.ReviewApi;
import com.hackmin.app.data.api.RiderApi;
import com.hackmin.app.data.api.UserApi;

import java.util.concurrent.TimeUnit;

/**
 * Central Retrofit setup.
 *
 * BASE_URL: point at your local Django dev server. On the Android emulator,
 * 10.0.2.2 maps to the host machine's localhost — use that instead of
 * "localhost" when running against `python manage.py runserver`.
 */
public final class ApiClient {

    private static final String BASE_URL = "http://hackmin.com/api/v1/";

    // GET 응답을 짧게(초 단위) 캐시해, 마이페이지에서 미리 받아두면 하위 화면 진입 시 즉시 표시된다.
    // 변경 요청(POST/PUT/DELETE·로그인/로그아웃 등)이 성공하면 캐시를 전부 비워 최신 데이터를 보장한다.
    private static final int GET_CACHE_SECONDS = 20;

    private static Retrofit retrofit;
    private static Cache httpCache;

    private ApiClient() {}

    /**
     * 이미지 등 미디어 절대경로 구성을 위한 서버 오리진(scheme+host+port).
     * BASE_URL에서 "/api/..." 앞부분만 잘라낸다. 예) http://54.116.95.188:8000
     * 서버가 상대경로(/media/...)를 줄 때 이 값을 앞에 붙여 절대 URL을 만든다.
     */
    public static String mediaBaseUrl() {
        int idx = BASE_URL.indexOf("/api/");
        return idx > 0 ? BASE_URL.substring(0, idx) : BASE_URL;
    }

    public static Retrofit getRetrofit(AuthInterceptor.TokenProvider tokenProvider) {
        return getRetrofit(tokenProvider, null);
    }

    public static synchronized Retrofit getRetrofit(
            AuthInterceptor.TokenProvider tokenProvider, File cacheDir
    ) {
        if (retrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    // 변경 요청(비-GET)이 성공하면 캐시 전체 무효화 → 삭제/등록/로그인 후 항상 최신.
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

            if (cacheDir != null) {
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

    // ══════════════════════════════════════════════════════════════
    //  Context 기반 편의 메서드 (권장)
    //
    //  각 화면에서 TokenProvider 람다를 직접 만들 필요 없이
    //  Context만 넘기면 SessionManager 싱글톤이 토큰을 자동으로 주입한다.
    //    예) ApiClient.restaurantApi(this).searchRestaurants(...)
    // ══════════════════════════════════════════════════════════════

    /** 임의의 Retrofit 서비스를 SessionManager 기반으로 생성한다. */
    public static <T> T api(Context context, Class<T> service) {
        SessionManager session = SessionManager.getInstance(context);
        return getRetrofit(session, context.getCacheDir()).create(service);
    }

    public static AuthApi authApi(Context context) { return api(context, AuthApi.class); }

    public static UserApi userApi(Context context) { return api(context, UserApi.class); }

    public static RestaurantApi restaurantApi(Context context) { return api(context, RestaurantApi.class); }

    public static LocationApi locationApi(Context context) { return api(context, LocationApi.class); }

    public static CartApi cartApi(Context context) { return api(context, CartApi.class); }

    public static OrderApi orderApi(Context context) { return api(context, OrderApi.class); }

    public static NoticeApi noticeApi(Context context) { return api(context, NoticeApi.class); }

    public static PaymentApi paymentApi(Context context) { return api(context, PaymentApi.class); }

    public static ReviewApi reviewApi(Context context) { return api(context, ReviewApi.class); }

    public static PromotionApi promotionApi(Context context) { return api(context, PromotionApi.class); }

    public static ChatbotApi chatbotApi(Context context) { return api(context, ChatbotApi.class); }

    public static InquiryApi inquiryApi(Context context) { return api(context, InquiryApi.class); }

    public static OwnerApi ownerApi(Context context) { return api(context, OwnerApi.class); }

    public static AdminApi adminApi(Context context) { return api(context, AdminApi.class); }

    public static RiderApi riderApi(Context context) { return api(context, RiderApi.class); }

    public static DownloadApi downloadApi(Context context) { return api(context, DownloadApi.class); }

    // ══════════════════════════════════════════════════════════════
    //  레거시: TokenProvider 직접 주입 방식 (하위 호환용)
    // ══════════════════════════════════════════════════════════════

    // ── 인증 ──
    public static AuthApi authApi(
            AuthInterceptor.TokenProvider tp) {
        return getRetrofit(tp).create(AuthApi.class);
    }

    // ── 사용자 (내 정보) ──
    public static UserApi userApi(
            AuthInterceptor.TokenProvider tp) {
        return getRetrofit(tp).create(UserApi.class);
    }

    // ── 음식점 / 메뉴 ──
    public static RestaurantApi restaurantApi(
            AuthInterceptor.TokenProvider tp) {
        return getRetrofit(tp).create(RestaurantApi.class);
    }

    // ── 위치 ──
    public static LocationApi locationApi(
            AuthInterceptor.TokenProvider tp) {
        return getRetrofit(tp).create(LocationApi.class);
    }

    // ── 장바구니 ──
    public static CartApi cartApi(
            AuthInterceptor.TokenProvider tp) {
        return getRetrofit(tp).create(CartApi.class);
    }

    // ── 주문 ──
    public static OrderApi orderApi(
            AuthInterceptor.TokenProvider tp) {
        return getRetrofit(tp).create(OrderApi.class);
    }

    // ── 결제 ──
    public static PaymentApi paymentApi(
            AuthInterceptor.TokenProvider tp) {
        return getRetrofit(tp).create(PaymentApi.class);
    }

    // ── 리뷰 ──
    public static ReviewApi reviewApi(
            AuthInterceptor.TokenProvider tp) {
        return getRetrofit(tp).create(ReviewApi.class);
    }

    // ── 프로모션 (쿠폰/찜/멤버십) ──
    public static PromotionApi promotionApi(
            AuthInterceptor.TokenProvider tp) {
        return getRetrofit(tp).create(PromotionApi.class);
    }

    // ── 챗봇 ──
    public static ChatbotApi chatbotApi(
            AuthInterceptor.TokenProvider tp) {
        return getRetrofit(tp).create(ChatbotApi.class);
    }

    // ── 사장님 ──
    public static OwnerApi ownerApi(
            AuthInterceptor.TokenProvider tp) {
        return getRetrofit(tp).create(OwnerApi.class);
    }

    // ── 관리자 ──
    public static AdminApi adminApi(
            AuthInterceptor.TokenProvider tp) {
        return getRetrofit(tp).create(AdminApi.class);
    }

    // ── 라이더 ──
    public static RiderApi riderApi(
            AuthInterceptor.TokenProvider tp) {
        return getRetrofit(tp).create(RiderApi.class);
    }

    // ── 다운로드 ──
    public static DownloadApi downloadApi(
            AuthInterceptor.TokenProvider tp) {
        return getRetrofit(tp).create(DownloadApi.class);
    }
}
