package com.hackmin.connect.data.model.rider;

import com.google.gson.annotations.SerializedName;

public class DeliveryStatusRequest {
    @SerializedName("status") private String status;
    // 배달 완료 시 앱이 GPS로 계산해 보고하는 이동 거리(km). 서버가 이 값으로 배달료를 산정한다.
    @SerializedName("distance_km") private Double distanceKm;

    public DeliveryStatusRequest(String status) {
        this.status = status;
    }

    public DeliveryStatusRequest(String status, double distanceKm) {
        this.status = status;
        this.distanceKm = distanceKm;
    }
}
