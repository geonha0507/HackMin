package com.hackmin.app.data.api;

import com.hackmin.app.data.model.cart.*;
import com.hackmin.app.data.model.common.MessageResponse;

import retrofit2.Call;
import retrofit2.http.*;

/**
 * 장바구니 API (/api/v1/cart)
 * 인증 필요 (Bearer token)
 */
public interface CartApi {

    /** 장바구니 조회 */
    @GET("cart")
    Call<CartDto> getCart();

    /** 장바구니에 메뉴 추가 */
    @POST("cart/items")
    Call<CartDto> addItem(@Body AddCartItemRequest request);

    /** 장바구니 항목 수량 변경 */
    @PUT("cart/items/{id}")
    Call<CartDto> updateItem(@Path("id") long itemId, @Body UpdateCartItemRequest request);

    /** 장바구니 항목 삭제 */
    @DELETE("cart/items/{id}")
    Call<Void> deleteItem(@Path("id") long itemId);

    /** 장바구니에 쿠폰 적용 */
    @POST("cart/coupon")
    Call<CartDto> applyCoupon(@Body ApplyCouponRequest request);

    /** 장바구니 쿠폰 해제 */
    @DELETE("cart/coupon")
    Call<CartDto> removeCoupon();

    /** 장바구니 요약 (금액 계산) */
    @GET("cart/summary")
    Call<CartSummaryDto> getCartSummary();
}
