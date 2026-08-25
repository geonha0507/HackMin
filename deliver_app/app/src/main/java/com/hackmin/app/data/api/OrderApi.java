package com.hackmin.app.data.api;

import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.order.*;

import java.util.Map;

import okhttp3.ResponseBody;
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

    /** [방어 ⑩] 수령확인 서명용 공개키 등록 (고객앱 Keystore EC 공개키) */
    @POST("orders/receipt-key")
    Call<ResponseBody> registerReceiptKey(@Body Map<String, String> body);

    /** [방어 ⑩] 고객 수령확인 — 네이티브 서명 헤더(X-Receipt-Ts/Nonce/Sig, X-Key-Id) 필수 */
    @POST("orders/{id}/confirm-receipt")
    Call<ResponseBody> confirmReceipt(@Path("id") long orderId,
                                      @HeaderMap Map<String, String> sigHeaders);
}
