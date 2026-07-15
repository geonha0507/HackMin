package com.hackmin.app.data.api;

import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * Maps to README Section 5 — 고객 — 장바구니 /cart
 */
public interface CartApi {

    @GET("cart")
    Call<Object> getCart();

    /** 🎯 vulnerable: validation bypass */
    @POST("cart/items")
    Call<Object> addCartItem(@Body Map<String, Object> item);

    /** 🎯 vulnerable: IDOR */
    @PUT("cart/items/{id}")
    Call<Object> updateCartItem(@Path("id") long itemId, @Body Map<String, Object> item);

    @DELETE("cart/items/{id}")
    Call<Void> deleteCartItem(@Path("id") long itemId);

    /** 🎯 vulnerable: coupon bypass */
    @POST("cart/coupon")
    Call<Object> applyCoupon(@Body Map<String, String> data);

    @GET("cart/summary")
    Call<Object> getCartSummary();
}
