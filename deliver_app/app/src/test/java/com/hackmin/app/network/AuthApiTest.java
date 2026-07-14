package com.hackmin.app.network;

import com.hackmin.app.data.api.AuthApi;
import com.hackmin.app.data.model.auth.LoginRequest;
import com.hackmin.app.data.model.auth.LoginResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static org.junit.Assert.*;

/**
 * Runs on the JVM (no emulator/device needed) — right click this file in
 * Android Studio and "Run" to execute it directly.
 *
 * This spins up a fake local HTTP server, so it does NOT hit your real
 * Django backend. It only proves that AuthApi + LoginRequest/LoginResponse
 * are wired correctly and that the DTO field names match what we expect
 * the server to send back.
 *
 * IMPORTANT: the mock JSON body below encodes our ASSUMPTION about the
 * server's real response shape (access/refresh/user). If your backend
 * teammate confirms different field names, update both this test's mock
 * body AND LoginResponse.java to match.
 */
public class AuthApiTest {

    private MockWebServer server;
    private AuthApi authApi;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/api/v1/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        authApi = retrofit.create(AuthApi.class);
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    public void login_parsesSuccessResponse() throws Exception {
        String mockJson = "{"
                + "\"access\":\"fake-access-token\","
                + "\"refresh\":\"fake-refresh-token\","
                + "\"user\":{"
                +   "\"id\":1,"
                +   "\"username\":\"alice\","
                +   "\"email\":\"alice@example.com\","
                +   "\"name\":\"Alice\","
                +   "\"phone\":\"010-1234-5678\","
                +   "\"role\":\"customer\","
                +   "\"created_at\":\"2026-01-01T00:00:00Z\""
                + "}}";

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(mockJson));

        Response<LoginResponse> response =
                authApi.login(new LoginRequest("alice", "pw1234")).execute();

        // 1. Did the call succeed and body parse at all?
        assertTrue(response.isSuccessful());
        assertNotNull(response.body());

        // 2. Do the fields actually land where we expect?
        LoginResponse body = response.body();
        assertEquals("fake-access-token", body.getAccessToken());
        assertEquals("fake-refresh-token", body.getRefreshToken());
        assertEquals("alice", body.getUser().getUsername());
        assertEquals("customer", body.getUser().getRole());

        // 3. Sanity check the request we actually sent matches expectations.
        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/api/v1/auth/login", recorded.getPath());
        assertTrue(recorded.getBody().readUtf8().contains("\"username\":\"alice\""));
    }

    @Test
    public void login_handlesErrorEnvelope() throws Exception {
        String mockError = "{\"code\":\"invalid_credentials\",\"message\":\"Invalid username or password\"}";

        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody(mockError));

        Response<LoginResponse> response =
                authApi.login(new LoginRequest("alice", "wrongpass")).execute();

        assertFalse(response.isSuccessful());
        assertEquals(401, response.code());
        // response.errorBody() is where you'd parse ApiErrorResponse in real code —
        // see the note in the network testing README about centralizing that.
    }
}
