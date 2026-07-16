package com.hackmin.app.network;

import androidx.annotation.NonNull;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;

/**
 * Attaches:
 *  - X-Hackmin-Mode, so the whole app can be flipped between vulnerable/secure
 *    from one place (e.g. a debug settings screen) for the pentest demo.
 *  - Authorization: Bearer <token>, when a token is present.
 *
 * Register this on the OkHttpClient used to build Retrofit (see ApiClient).
 */
public class HackminModeInterceptor implements Interceptor {

    private final ModeProvider modeProvider;
    private final TokenProvider tokenProvider;

    public HackminModeInterceptor(ModeProvider modeProvider, TokenProvider tokenProvider) {
        this.modeProvider = modeProvider;
        this.tokenProvider = tokenProvider;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        Request.Builder builder = original.newBuilder()
                .header("X-Hackmin-Mode", modeProvider.getMode().getHeaderValue());

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

    /** Lets the interceptor read the current mode without depending on Android UI classes. */
    public interface ModeProvider {
        HackminMode getMode();
    }

    /** Lets the interceptor read the current JWT without depending on your storage impl. */
    public interface TokenProvider {
        String getAccessToken();
    }
}
