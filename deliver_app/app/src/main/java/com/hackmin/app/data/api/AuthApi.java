package com.hackmin.app.data.api;
import com.hackmin.app.data.model.auth.*;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.Map;

/**
 * Maps to README Section 1 — 인증 /auth
 * Base path is prefixed with /api/v1 via ApiClient's baseUrl.
 */
public interface AuthApi {

    @POST("auth/signup")
    Call<UserDto> signup(@Body SignupRequest request);

    @POST("auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("auth/logout")
    Call<Void> logout();

    @POST("auth/refresh")
    Call<RefreshResponse> refresh(@Body RefreshRequest request);

    /** 아이디 중복확인: checkDuplicateUsername("testuser") → ?username=testuser */
    @GET("auth/check-duplicate")
    Call<DuplicateCheckResponse> checkDuplicateUsername(@Query("username") String username);

    /** 닉네임 중복확인: checkDuplicateNickname("홍길동") → ?nickname=홍길동 */
    @GET("auth/check-duplicate")
    Call<DuplicateCheckResponse> checkDuplicateNickname(@Query("nickname") String nickname);

    /** 이메일 중복확인: checkDuplicateEmail("a@b.com") → ?email=a@b.com */
    @GET("auth/check-duplicate")
    Call<DuplicateCheckResponse> checkDuplicateEmail(@Query("email") String email);

    @POST("auth/password/reset-request")
    Call<Void> requestPasswordReset(@Body Map<String, String> body);

    @POST("auth/password/reset")
    Call<Void> resetPassword(@Body PasswordResetDto request);
}
