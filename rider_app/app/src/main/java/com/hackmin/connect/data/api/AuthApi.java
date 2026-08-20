package com.hackmin.connect.data.api;

import com.hackmin.connect.data.model.auth.DuplicateCheckResponse;
import com.hackmin.connect.data.model.auth.LoginRequest;
import com.hackmin.connect.data.model.auth.LoginResponse;
import com.hackmin.connect.data.model.auth.RefreshRequest;
import com.hackmin.connect.data.model.auth.RefreshResponse;
import com.hackmin.connect.data.model.auth.SignupRequest;
import com.hackmin.connect.data.model.auth.UserDto;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * 인증 /auth — deliver_app과 같은 엔드포인트를 쓴다.
 * 회원가입은 role=rider 로 보낸다(SignupRequest 참고).
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

    /** 아이디 중복확인: ?username=... */
    @GET("auth/check-duplicate")
    Call<DuplicateCheckResponse> checkDuplicateUsername(@Query("username") String username);

    /** 닉네임 중복확인: ?nickname=... */
    @GET("auth/check-duplicate")
    Call<DuplicateCheckResponse> checkDuplicateNickname(@Query("nickname") String nickname);
}
