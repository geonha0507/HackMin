package com.hackmin.app.data.api;

import java.util.Map;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * Maps to README Section 6 — 고객 — 주문 /orders
 */
public interface OrderApi {

    /** 🎯 vulnerable: price manipulation */
    @POST("orders")
    Call<Object> createOrder(@Body Map<String, Object> order);

    /** 🎯 vulnerable: IDOR */
    @GET("orders/{id}")
    Call<Object> getOrderDetail(@Path("id") long orderId);

    @GET("orders/{id}/status")
    Call<Object> getOrderStatus(@Path("id") long orderId);

    /** 🎯 vulnerable: IDOR · status bypass */
    @POST("orders/{id}/cancel")
    Call<Void> cancelOrder(@Path("id") long orderId);

    @POST("orders/{id}/reorder")
    Call<Object> reorder(@Path("id") long orderId);
}
