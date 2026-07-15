package com.hackmin.app.data.api;

import com.hackmin.app.data.model.auth.UserDto;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * Maps to README Section 2 — 고객 — 마이페이지 /me
 */
public interface MyPageApi {

    @GET("me")
    Call<UserDto> getMyInfo();

    /** 🎯 vulnerable: Mass Assignment */
    @PUT("me")
    Call<UserDto> updateMyInfo(@Body Map<String, Object> fields);

    @DELETE("me")
    Call<Void> deleteAccount();

    @PUT("me/password")
    Call<Void> changePassword(@Body Map<String, String> data);

    @GET("me/addresses")
    Call<List<Object>> getAddresses();

    @POST("me/addresses")
    Call<Object> addAddress(@Body Map<String, Object> address);

    /** 🎯 vulnerable: IDOR */
    @GET("me/orders")
    Call<List<Object>> getOrders();

    @GET("me/reviews")
    Call<List<Object>> getReviews();

    @GET("me/coupons")
    Call<List<Object>> getCoupons();

    @GET("me/membership")
    Call<Object> getMembership();
}
