package com.hackmin.app.data.api;

import java.util.Map;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * Maps to README Section 7 — 고객 — 결제 /payments
 */
public interface PaymentApi {

    /** 🎯 vulnerable: IDOR · amount manipulation */
    @POST("payments")
    Call<Object> createPayment(@Body Map<String, Object> payment);

    @GET("payments/{id}")
    Call<Object> getPaymentDetail(@Path("id") long paymentId);

    /** 🎯 vulnerable: IDOR */
    @POST("payments/{id}/cancel")
    Call<Void> cancelPayment(@Path("id") long paymentId);

    /** 🎯 vulnerable: amount validation missing */
    @POST("payments/{id}/refund")
    Call<Void> requestRefund(@Path("id") long paymentId, @Body Map<String, Object> refundData);
}
