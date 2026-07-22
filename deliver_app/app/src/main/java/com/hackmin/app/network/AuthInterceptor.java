package com.hackmin.app.network;

import androidx.annotation.NonNull;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;

/**
 * Attaches Authorization: Bearer <token> when a token is present, except on the
 * public auth endpoints (login/signup/refresh/check-duplicate/password) where a
 * stale token would make the server reject the request before login.
 *
 * Register this on the OkHttpClient used to build Retrofit (see ApiClient).
 */
public class AuthInterceptor implements Interceptor {

    private final TokenProvider tokenProvider;

    public AuthInterceptor(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        Request.Builder builder = original.newBuilder();

        // 공개 인증 엔드포인트(login/signup/refresh/check-duplicate/password)에는
        // 토큰을 붙이지 않는다. 만료된 토큰이 붙으면 서버가 로그인 전에 토큰을
        // 검증하려다 401을 내서 로그인 자체가 막히기 때문(stale token 문제).
        String token = tokenProvider.getAccessToken();
        if (token != null && !token.isEmpty()
                && !isPublicAuthEndpoint(original.url().encodedPath())) {
            builder.header("Authorization", "Bearer " + token);
        }

        return chain.proceed(builder.build());
    }

    /** 토큰 없이 호출해야 하는 공개 인증 엔드포인트인지. */
    private boolean isPublicAuthEndpoint(String path) {
        if (path == null) return false;
        return path.contains("/auth/login")
                || path.contains("/auth/signup")
                || path.contains("/auth/refresh")
                || path.contains("/auth/check-duplicate")
                || path.contains("/auth/password");
    }

    /** Lets the interceptor read the current JWT without depending on your storage impl. */
    public interface TokenProvider {
        String getAccessToken();
    }
}
