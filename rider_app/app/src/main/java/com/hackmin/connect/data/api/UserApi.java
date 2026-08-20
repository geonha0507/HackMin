package com.hackmin.connect.data.api;

import com.hackmin.connect.data.model.user.UserProfileDto;

import retrofit2.Call;
import retrofit2.http.GET;

/**
 * 사용자 (내 정보) API (/api/v1/me) — 인증 필요 (Bearer token)
 */
public interface UserApi {

    /** 내 정보 조회 */
    @GET("me")
    Call<UserProfileDto> getMe();
}
