package com.hackmin.app.network;

import android.content.Context;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import com.hackmin.app.data.api.AdminApi;
import com.hackmin.app.data.api.AuthApi;
import com.hackmin.app.data.api.CartApi;
import com.hackmin.app.data.api.ChatbotApi;
import com.hackmin.app.data.api.DownloadApi;
import com.hackmin.app.data.api.LocationApi;
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

    private static final String BASE_URL = "http://54.116.95.188:8000/api/v1/";

    private static Retrofit retrofit;

    private ApiClient() {}

    public static synchronized Retrofit getRetrofit(
            HackminModeInterceptor.ModeProvider modeProvider,
            HackminModeInterceptor.TokenProvider tokenProvider
    ) {
        if (retrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(new HackminModeInterceptor(modeProvider, tokenProvider))
                    .addInterceptor(logging)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    // ══════════════════════════════════════════════════════════════
    //  Context 기반 편의 메서드 (권장)
    //
    //  각 화면에서 ModeProvider/TokenProvider 람다를 직접 만들 필요 없이
    //  Context만 넘기면 SessionManager 싱글톤이 토큰/모드를 자동으로 주입한다.
    //    예) ApiClient.restaurantApi(this).searchRestaurants(...)
    // ══════════════════════════════════════════════════════════════

    /** 임의의 Retrofit 서비스를 SessionManager 기반으로 생성한다. */
    public static <T> T api(Context context, Class<T> service) {
        SessionManager session = SessionManager.getInstance(context);
        return getRetrofit(session, session).create(service);
    }

    public static AuthApi authApi(Context context) { return api(context, AuthApi.class); }

    public static UserApi userApi(Context context) { return api(context, UserApi.class); }

    public static RestaurantApi restaurantApi(Context context) { return api(context, RestaurantApi.class); }

    public static LocationApi locationApi(Context context) { return api(context, LocationApi.class); }

    public static CartApi cartApi(Context context) { return api(context, CartApi.class); }

    public static OrderApi orderApi(Context context) { return api(context, OrderApi.class); }

    public static PaymentApi paymentApi(Context context) { return api(context, PaymentApi.class); }

    public static ReviewApi reviewApi(Context context) { return api(context, ReviewApi.class); }

    public static PromotionApi promotionApi(Context context) { return api(context, PromotionApi.class); }

    public static ChatbotApi chatbotApi(Context context) { return api(context, ChatbotApi.class); }

    public static OwnerApi ownerApi(Context context) { return api(context, OwnerApi.class); }

    public static AdminApi adminApi(Context context) { return api(context, AdminApi.class); }

    public static RiderApi riderApi(Context context) { return api(context, RiderApi.class); }

    public static DownloadApi downloadApi(Context context) { return api(context, DownloadApi.class); }

    // ══════════════════════════════════════════════════════════════
    //  레거시: ModeProvider/TokenProvider 직접 주입 방식 (하위 호환용)
    // ══════════════════════════════════════════════════════════════

    // ── 인증 ──
    public static AuthApi authApi(
            HackminModeInterceptor.ModeProvider mp,
            HackminModeInterceptor.TokenProvider tp) {
        return getRetrofit(mp, tp).create(AuthApi.class);
    }

    // ── 사용자 (내 정보) ──
    public static UserApi userApi(
            HackminModeInterceptor.ModeProvider mp,
            HackminModeInterceptor.TokenProvider tp) {
        return getRetrofit(mp, tp).create(UserApi.class);
    }

    // ── 음식점 / 메뉴 ──
    public static RestaurantApi restaurantApi(
            HackminModeInterceptor.ModeProvider mp,
            HackminModeInterceptor.TokenProvider tp) {
        return getRetrofit(mp, tp).create(RestaurantApi.class);
    }

    // ── 위치 ──
    public static LocationApi locationApi(
            HackminModeInterceptor.ModeProvider mp,
            HackminModeInterceptor.TokenProvider tp) {
        return getRetrofit(mp, tp).create(LocationApi.class);
    }

    // ── 장바구니 ──
    public static CartApi cartApi(
            HackminModeInterceptor.ModeProvider mp,
            HackminModeInterceptor.TokenProvider tp) {
        return getRetrofit(mp, tp).create(CartApi.class);
    }

    // ── 주문 ──
    public static OrderApi orderApi(
            HackminModeInterceptor.ModeProvider mp,
            HackminModeInterceptor.TokenProvider tp) {
        return getRetrofit(mp, tp).create(OrderApi.class);
    }

    // ── 결제 ──
    public static PaymentApi paymentApi(
            HackminModeInterceptor.ModeProvider mp,
            HackminModeInterceptor.TokenProvider tp) {
        return getRetrofit(mp, tp).create(PaymentApi.class);
    }

    // ── 리뷰 ──
    public static ReviewApi reviewApi(
            HackminModeInterceptor.ModeProvider mp,
            HackminModeInterceptor.TokenProvider tp) {
        return getRetrofit(mp, tp).create(ReviewApi.class);
    }

    // ── 프로모션 (쿠폰/찜/멤버십) ──
    public static PromotionApi promotionApi(
            HackminModeInterceptor.ModeProvider mp,
            HackminModeInterceptor.TokenProvider tp) {
        return getRetrofit(mp, tp).create(PromotionApi.class);
    }

    // ── 챗봇 ──
    public static ChatbotApi chatbotApi(
            HackminModeInterceptor.ModeProvider mp,
            HackminModeInterceptor.TokenProvider tp) {
        return getRetrofit(mp, tp).create(ChatbotApi.class);
    }

    // ── 사장님 ──
    public static OwnerApi ownerApi(
            HackminModeInterceptor.ModeProvider mp,
            HackminModeInterceptor.TokenProvider tp) {
        return getRetrofit(mp, tp).create(OwnerApi.class);
    }

    // ── 관리자 ──
    public static AdminApi adminApi(
            HackminModeInterceptor.ModeProvider mp,
            HackminModeInterceptor.TokenProvider tp) {
        return getRetrofit(mp, tp).create(AdminApi.class);
    }

    // ── 라이더 ──
    public static RiderApi riderApi(
            HackminModeInterceptor.ModeProvider mp,
            HackminModeInterceptor.TokenProvider tp) {
        return getRetrofit(mp, tp).create(RiderApi.class);
    }

    // ── 다운로드 ──
    public static DownloadApi downloadApi(
            HackminModeInterceptor.ModeProvider mp,
            HackminModeInterceptor.TokenProvider tp) {
        return getRetrofit(mp, tp).create(DownloadApi.class);
    }
}
