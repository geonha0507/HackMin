package com.hackmin.app.data.api;

import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.order.*;

import retrofit2.Call;
import retrofit2.http.*;

/**
 * 주문 API (/api/v1/orders, /api/v1/me/orders)
 * 인증 필요 (Bearer token)
 */
public interface OrderApi {

    /** 주문 생성 (장바구니 → 주문 전환) */
    @POST("orders")
    Call<OrderDto> createOrder(@Body OrderCreateRequest request);

    /** 주문 상세 조회 */
    @GET("orders/{id}")
    Call<OrderDto> getOrder(@Path("id") long orderId);

    /** 주문 상태 변경 */
    @PUT("orders/{id}/status")
    Call<OrderDto> updateOrderStatus(@Path("id") long orderId, @Body OrderStatusRequest request);

    /** 주문 취소 */
    @POST("orders/{id}/cancel")
    Call<OrderDto> cancelOrder(@Path("id") long orderId);

    /** 재주문 */
    @POST("orders/{id}/reorder")
    Call<OrderDto> reorder(@Path("id") long orderId);

    /** 내 주문 내역 조회 */
    @GET("me/orders")
    Call<PagedResponse<OrderDto>> getMyOrders(
            @Query("status") String status,
            @Query("page") Integer page
    );
}
