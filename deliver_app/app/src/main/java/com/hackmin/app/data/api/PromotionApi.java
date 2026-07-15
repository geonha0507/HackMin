package com.hackmin.app.data.api;

import com.hackmin.app.data.model.common.MessageResponse;
import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.promotion.*;

import retrofit2.Call;
import retrofit2.http.*;

/**
 * 프로모션 API — 쿠폰, 찜, 멤버십 (/api/v1/coupons, /favorites, /membership)
 * 인증 필요 (Bearer token)
 */
public interface PromotionApi {

    // ── 쿠폰 ──

    /** 다운로드 가능 쿠폰 목록 */
    @GET("coupons")
    Call<PagedResponse<CouponDto>> getCoupons(@Query("page") Integer page);

    /** 쿠폰 코드로 등록 */
    @POST("coupons/register")
    Call<UserCouponDto> registerCoupon(@Body RegisterCouponRequest request);

    /** 쿠폰 다운로드 */
    @POST("coupons/{id}/download")
    Call<UserCouponDto> downloadCoupon(@Path("id") long couponId);

    /** 내 쿠폰 목록 */
    @GET("me/coupons")
    Call<PagedResponse<UserCouponDto>> getMyCoupons(@Query("page") Integer page);

    // ── 찜 (즐겨찾기) ──

    /** 찜 목록 조회 */
    @GET("favorites")
    Call<PagedResponse<FavoriteDto>> getFavorites(@Query("page") Integer page);

    /** 찜 등록 */
    @POST("favorites")
    Call<FavoriteDto> addFavorite(@Body AddFavoriteRequest request);

    /** 찜 해제 */
    @DELETE("favorites/{id}")
    Call<Void> deleteFavorite(@Path("id") long favoriteId);

    // ── 멤버십 ──

    /** 멤버십 가입 */
    @POST("membership/subscribe")
    Call<MembershipDto> subscribe(@Body MembershipSubscribeRequest request);

    /** 멤버십 해지 */
    @POST("membership/cancel")
    Call<MembershipDto> cancelMembership();

    /** 멤버십 혜택 조회 */
    @GET("membership/benefits")
    Call<MembershipBenefitsDto> getMembershipBenefits();

    /** 멤버십 결제 내역 */
    @GET("membership/payments")
    Call<PagedResponse<MembershipPaymentDto>> getMembershipPayments(@Query("page") Integer page);

    /** 내 멤버십 정보 */
    @GET("me/membership")
    Call<MembershipDto> getMyMembership();
}
