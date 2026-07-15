package com.hackmin.app.data.api;

import com.hackmin.app.data.model.payment.*;

import retrofit2.Call;
import retrofit2.http.*;

/**
 * 결제 API (/api/v1/payments)
 * 인증 필요 (Bearer token)
 */
public interface PaymentApi {

    /** 결제 생성 */
    @POST("payments")
    Call<PaymentDto> createPayment(@Body PaymentCreateRequest request);

    /** 결제 상세 조회 */
    @GET("payments/{id}")
    Call<PaymentDto> getPayment(@Path("id") long paymentId);

    /** 결제 취소 */
    @POST("payments/{id}/cancel")
    Call<PaymentDto> cancelPayment(@Path("id") long paymentId);

    /** 환불 요청 */
    @POST("payments/{id}/refund")
    Call<RefundDto> refundPayment(@Path("id") long paymentId, @Body RefundRequest request);
}
