package com.hackmin.app.network;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.concurrent.TimeUnit;

/**
 * Central Retrofit setup.
 *
 * BASE_URL: point at your local Django dev server. On the Android emulator,
 * 10.0.2.2 maps to the host machine's localhost — use that instead of
 * "localhost" when running against `python manage.py runserver`.
 *
 * Swap ModeProvider/TokenProvider implementations for real ones backed by
 * e.g. a debug-menu setting and encrypted SharedPreferences/DataStore.
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
            // BODY logging is convenient for a hacking-focused capstone (you
            // want to see the raw vulnerable-mode payloads) but strip this
            // for anything resembling a release build.
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
}
