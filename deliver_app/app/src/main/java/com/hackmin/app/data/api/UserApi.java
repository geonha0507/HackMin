package com.hackmin.app.data.api;

import com.hackmin.app.data.model.common.MessageResponse;
import com.hackmin.app.data.model.user.*;

import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

/**
 * 사용자 (내 정보) API (/api/v1/me)
 * 인증 필요 (Bearer token)
 */
public interface UserApi {

    /** 내 정보 조회 */
    @GET("me")
    Call<UserProfileDto> getMe();

    /** 내 정보 수정 */
    @PUT("me")
    Call<UserProfileDto> updateMe(@Body UpdateProfileRequest request);

    /** 비밀번호 변경 */
    @PUT("me/password")
    Call<MessageResponse> changePassword(@Body ChangePasswordRequest request);

    /** 배송지 목록 조회 */
    @GET("me/addresses")
    Call<List<AddressDto>> getAddresses();

    /** 배송지 등록 */
    @POST("me/addresses")
    Call<AddressDto> createAddress(@Body AddressDto address);

    /** 배송지 수정 */
    @PUT("me/addresses/{id}")
    Call<AddressDto> updateAddress(@Path("id") long addressId, @Body AddressDto address);

    /** 배송지 삭제 */
    @DELETE("me/addresses/{id}")
    Call<Void> deleteAddress(@Path("id") long addressId);
}
