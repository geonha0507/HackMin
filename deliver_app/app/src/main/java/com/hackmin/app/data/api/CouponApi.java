package com.hackmin.app.data.api;

import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * Maps to README Section 9 — 고객 — 쿠폰 / 찜 / 챗봇 / 멤버십
 */
public interface CouponApi {

    /** 🎯 vulnerable: code exposure */
    @GET("coupons")
    Call<List<Object>> getAvailableCoupons();

    /** 🎯 vulnerable: duplicate issuance */
    @POST("coupons/{id}/download")
    Call<Void> downloadCoupon(@Path("id") long couponId);

    @POST("coupons/register")
    Call<Void> registerCoupon(@Body Map<String, String> data);

    @GET("favorites")
    Call<List<Object>> getFavorites();

    @POST("favorites")
    Call<Void> addFavorite(@Body Map<String, Long> data);

    @DELETE("favorites/{id}")
    Call<Void> removeFavorite(@Path("id") long restaurantId);

    /** 🎯 vulnerable: SSTI */
    @POST("chatbot/message")
    Call<Object> sendChatbotMessage(@Body Map<String, String> message);

    /** 🎯 vulnerable: payment bypass */
    @POST("membership/subscribe")
    Call<Void> subscribeMembership(@Body Map<String, Object> data);

    @POST("membership/cancel")
    Call<Void> cancelMembership();

    @GET("membership/benefits")
    Call<Object> getMembershipBenefits();

    @GET("membership/payments")
    Call<List<Object>> getMembershipPayments();
}
