package com.hackmin.app.network;

import com.hackmin.app.data.api.*;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Central Retrofit setup.
 */
public final class ApiClient {

    private static final String BASE_URL = "http://10.0.2.2:8000/api/v1/";

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

    public static AuthApi authApi(
            HackminModeInterceptor.ModeProvider modeProvider,
            HackminModeInterceptor.TokenProvider tokenProvider
    ) {
        return getRetrofit(modeProvider, tokenProvider).create(AuthApi.class);
    }

    public static RestaurantApi restaurantApi(
            HackminModeInterceptor.ModeProvider modeProvider,
            HackminModeInterceptor.TokenProvider tokenProvider
    ) {
        return getRetrofit(modeProvider, tokenProvider).create(RestaurantApi.class);
    }

    public static MyPageApi myPageApi(
            HackminModeInterceptor.ModeProvider modeProvider,
            HackminModeInterceptor.TokenProvider tokenProvider
    ) {
        return getRetrofit(modeProvider, tokenProvider).create(MyPageApi.class);
    }

    public static CartApi cartApi(
            HackminModeInterceptor.ModeProvider modeProvider,
            HackminModeInterceptor.TokenProvider tokenProvider
    ) {
        return getRetrofit(modeProvider, tokenProvider).create(CartApi.class);
    }

    public static OrderApi orderApi(
            HackminModeInterceptor.ModeProvider modeProvider,
            HackminModeInterceptor.TokenProvider tokenProvider
    ) {
        return getRetrofit(modeProvider, tokenProvider).create(OrderApi.class);
    }

    public static PaymentApi paymentApi(
            HackminModeInterceptor.ModeProvider modeProvider,
            HackminModeInterceptor.TokenProvider tokenProvider
    ) {
        return getRetrofit(modeProvider, tokenProvider).create(PaymentApi.class);
    }

    public static ReviewApi reviewApi(
            HackminModeInterceptor.ModeProvider modeProvider,
            HackminModeInterceptor.TokenProvider tokenProvider
    ) {
        return getRetrofit(modeProvider, tokenProvider).create(ReviewApi.class);
    }

    public static CouponApi couponApi(
            HackminModeInterceptor.ModeProvider modeProvider,
            HackminModeInterceptor.TokenProvider tokenProvider
    ) {
        return getRetrofit(modeProvider, tokenProvider).create(CouponApi.class);
    }
}
