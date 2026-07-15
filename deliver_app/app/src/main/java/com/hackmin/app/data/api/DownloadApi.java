package com.hackmin.app.data.api;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * 파일 다운로드 API (/api/v1/downloads)
 * 인증 필요 (Bearer token)
 */
public interface DownloadApi {

    /** 주문 영수증 다운로드 */
    @Streaming
    @GET("downloads/receipt/{orderId}")
    Call<ResponseBody> downloadReceipt(@Path("orderId") long orderId);

    /** 매출 리포트 다운로드 (사장님) */
    @Streaming
    @GET("downloads/sales-report/{id}")
    Call<ResponseBody> downloadSalesReport(@Path("id") long restaurantId);

    /** 사업자등록증 다운로드 */
    @Streaming
    @GET("downloads/business-license/{id}")
    Call<ResponseBody> downloadBusinessLicense(@Path("id") long restaurantId);

    /** 주문 내역 다운로드 */
    @Streaming
    @GET("downloads/order-history/{id}")
    Call<ResponseBody> downloadOrderHistory(@Path("id") long userId);

    /** 첨부파일 다운로드 */
    @Streaming
    @GET("downloads/attachment/{id}")
    Call<ResponseBody> downloadAttachment(@Path("id") long attachmentId);
}
