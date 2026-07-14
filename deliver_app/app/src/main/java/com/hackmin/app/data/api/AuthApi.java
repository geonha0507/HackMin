package com.hackmin.app.data.api;

import com.hackmin.app.model.dto.auth.*;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * Maps to README Section 1 — 인증 /auth
 * Base path is prefixed with /api/v1 via ApiClient's baseUrl.
 *
 * NOTE: email/phone/OTP verification endpoints are explicitly out of scope
 * per the README, so they're intentionally omitted here.
 */
public interface AuthApi {

    @POST("auth/signup")
    Call<UserDto> signup(@Body SignupRequest request);

    // 🎯 vulnerable/secure dual-mode (SQL Injection in vulnerable mode)
    @POST("auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("auth/logout")
    Call<Void> logout();

    // 🎯 vulnerable mode skips signature verification
    @POST("auth/refresh")
    Call<RefreshResponse> refresh(@Body RefreshRequest request);

    @GET("auth/check-duplicate")
    Call<DuplicateCheckResponse> checkDuplicate(
            @Query("field") String field,   // "username" | "email"
            @Query("value") String value
    );

    // 🎯 vulnerable mode allows account enumeration
    @POST("auth/password/reset-request")
    Call<Void> requestPasswordReset(@Body PasswordResetRequestDto request);

    @POST("auth/password/reset")
    Call<Void> resetPassword(@Body PasswordResetDto request);
}
