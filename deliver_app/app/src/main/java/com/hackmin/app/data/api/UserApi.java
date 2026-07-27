package com.hackmin.app.data.api;

import com.hackmin.app.data.model.common.MessageResponse;
import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.user.*;

import retrofit2.Call;
import retrofit2.http.*;

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

    /** 회원 탈퇴 (204 No Content) */
    @DELETE("me")
    Call<Void> withdraw();

    /** 배송지 목록 조회 (서버 응답은 {count,next,previous,results} 페이지 형태) — [C] 실제 계약에 맞춰 수정 */
    @GET("me/addresses")
    Call<PagedResponse<AddressDto>> getAddresses();

    /** 배송지 등록 */
    @POST("me/addresses")
    Call<AddressDto> createAddress(@Body AddressDto address);

    /** 배송지 수정 */
    @PUT("me/addresses/{id}")
    Call<AddressDto> updateAddress(@Path("id") long addressId, @Body AddressDto address);

    /** 배송지 삭제 */
    @DELETE("me/addresses/{id}")
    Call<Void> deleteAddress(@Path("id") long addressId);

    /** 등록 카드/간편결제 목록 (provider: card|kakao|naver, 생략 시 전체) */
    @GET("me/payment-cards")
    Call<PagedResponse<PaymentCardDto>> getPaymentCards(@Query("provider") String provider);

    /** 결제 카드/간편결제 등록 (카드번호는 서버가 AES-256 암호화 저장). 등록된 값(마스킹) 반환 */
    @POST("me/payment-cards")
    Call<PaymentCardDto> registerPaymentCard(@Body CardRegisterRequest request);

    /** 등록 카드/간편결제 삭제 */
    @DELETE("me/payment-cards/{id}")
    Call<Void> deletePaymentCard(@Path("id") long cardId);

    /** 결제 비밀번호 설정 여부 조회 */
    @GET("me/payment-password")
    Call<PaymentPasswordResponse> getPaymentPasswordStatus();

    /** 결제 비밀번호(6자리) 설정 */
    @POST("me/payment-password")
    Call<PaymentPasswordResponse> setPaymentPassword(@Body PaymentPasswordRequest request);

    /** 결제 비밀번호(6자리) 검증 */
    @POST("me/payment-password/verify")
    Call<PaymentPasswordResponse> verifyPaymentPassword(@Body PaymentPasswordRequest request);

    /** 등록 계좌 목록 조회 (응답: {results:[...]}) */
    @GET("me/bank-accounts")
    Call<PagedResponse<BankAccountDto>> getBankAccounts();

    /** 계좌 등록 (계좌번호는 서버가 AES-256 암호화 저장). 등록된 계좌(마스킹값)를 반환 */
    @POST("me/bank-accounts")
    Call<BankAccountDto> registerBankAccount(@Body AccountRegisterRequest request);

    /** 등록 계좌 삭제 */
    @DELETE("me/bank-accounts/{id}")
    Call<Void> deleteBankAccount(@Path("id") long accountId);
}
