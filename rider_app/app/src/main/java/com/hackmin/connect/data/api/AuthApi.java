package com.hackmin.connect.data.api;

import com.hackmin.connect.data.model.auth.LoginRequest;
import com.hackmin.connect.data.model.auth.LoginResponse;
import com.hackmin.connect.data.model.auth.RefreshRequest;
import com.hackmin.connect.data.model.auth.RefreshResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * 인증 /auth — 라이더 앱은 로그인/로그아웃/토큰 갱신만 쓴다.
 * (라이더 계정은 가입 엔드포인트로 만들 수 없고 관리자가 발급한다)
 */
public interface AuthApi {

    @POST("auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("auth/logout")
    Call<Void> logout();

    @POST("auth/refresh")
    Call<RefreshResponse> refresh(@Body RefreshRequest request);
}
