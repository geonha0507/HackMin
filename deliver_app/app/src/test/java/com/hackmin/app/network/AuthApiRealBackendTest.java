package com.hackmin.app.network;

import com.hackmin.app.data.api.AuthApi;
import com.hackmin.app.data.model.auth.LoginRequest;
import com.hackmin.app.data.model.auth.LoginResponse;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Test;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static org.junit.Assert.*;

/**
 * REAL integration test — hits your actual Docker container, not a mock.
 * Requires the backend to be running locally (docker ps should show the
 * hackmin container with port 8000 mapped to the host).
 *
 * This runs on your own JVM (not an emulator), so it reaches Docker via
 * plain "localhost" -- same as curl would from your terminal. Do NOT use
 * 10.0.2.2 here; that only applies when the app itself runs on an
 * Android emulator.
 *
 * If this fails with a connection error, the backend isn't reachable at
 * this URL -- check `docker ps` for the actual host port mapping.
 *
 * If this fails with an assertion error, the backend is reachable but
 * returned a different JSON shape than LoginResponse.java assumes --
 * that's the useful signal this test exists to catch.
 */
public class AuthApiRealBackendTest {

    private static final String REAL_BASE_URL = "http://127.0.0.1:8000/api/v1/";

    @Test
    public void login_againstRealBackend_returnsExpectedShape() throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> chain.proceed(
                        chain.request().newBuilder()
                                .header("X-Hackmin-Mode", "secure")
                                .build()))
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(REAL_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        AuthApi authApi = retrofit.create(AuthApi.class);

        retrofit2.Response<LoginResponse> response =
                authApi.login(new LoginRequest("alice", "pw1234")).execute();

        System.out.println("HTTP status: " + response.code());

        if (!response.isSuccessful()) {
            // Print the raw error body so you can see exactly what came back,
            // since our ApiErrorResponse assumption might also be wrong.
            String errorBody = response.errorBody() != null
                    ? response.errorBody().string() : "(empty)";
            System.out.println("Error body: " + errorBody);
            fail("Login failed against real backend. See printed error body above. "
                    + "HTTP " + response.code());
        }

        LoginResponse body = response.body();
        assertNotNull("Response body was null -- check REAL_BASE_URL and that the "
                + "container is actually running", body);

        System.out.println("access token present: " + (body.getAccessToken() != null));
        System.out.println("refresh token present: " + (body.getRefreshToken() != null));
        System.out.println("user: " + (body.getUser() != null
                ? body.getUser().getUsername() + " / " + body.getUser().getRole()
                : "null -- 'user' field name assumption may be wrong"));

        assertNotNull("access token missing -- check real field name isn't "
                + "e.g. access_token instead of access", body.getAccessToken());
    }

    /** Simple raw-OkHttp health check, no Retrofit/DTOs involved -- useful if
     *  the test above fails and you want to rule out connectivity issues first. */
    @Test
    public void health_endpointReachable() throws Exception {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("http://localhost:8000/api/v1/health")
                .build();

        try (Response response = client.newCall(request).execute()) {
            System.out.println("Health check status: " + response.code());
            assertTrue("Backend not reachable at localhost:8000 -- check docker ps "
                    + "for the actual port mapping", response.isSuccessful());
        }
    }
}