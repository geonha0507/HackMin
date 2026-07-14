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

        String token = tokenProvider.getAccessToken();
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }

        return chain.proceed(builder.build());
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
