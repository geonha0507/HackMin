package com.hackmin.connect.data.api;

import com.hackmin.connect.data.model.common.PagedResponse;
import com.hackmin.connect.data.model.rider.*;

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

    /** 내 실시간 위치 전송(운행 중 주기적으로 호출) */
    @PUT("rider/location")
    Call<RiderLocationDto> updateLocation(@Body RiderLocationDto location);

    /** 마지막으로 저장된 내 위치 조회(없으면 204) */
    @GET("rider/location")
    Call<RiderLocationDto> getLocation();

    /** 해킹의 민족 전체 메뉴 목록(홈 노출용, 사진 포함) */
    @GET("rider/menus")
    Call<PagedResponse<MenuDto>> getMenus(@Query("limit") Integer limit);
}
