package com.hackmin.app.data.api;

import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.rider.*;

import retrofit2.Call;
import retrofit2.http.*;

/**
 * 라이더 API (/api/v1/rider)
 * 인증 필요 (Bearer token, role=rider)
 */
public interface RiderApi {

    /** 배달 목록 조회 */
    @GET("rider/deliveries")
    Call<PagedResponse<DeliveryDto>> getDeliveries(
            @Query("status") String status,
            @Query("page") Integer page
    );

    /** 배달 상세 조회 */
    @GET("rider/deliveries/{id}")
    Call<DeliveryDetailDto> getDelivery(@Path("id") long deliveryId);

    /** 배달 상태 변경 (픽업/배달중/완료) */
    @PUT("rider/deliveries/{id}/status")
    Call<DeliveryDetailDto> updateDeliveryStatus(
            @Path("id") long deliveryId,
            @Body DeliveryStatusRequest request
    );
}
