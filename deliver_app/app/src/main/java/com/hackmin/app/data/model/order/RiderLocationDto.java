package com.hackmin.app.data.model.order;

import com.google.gson.annotations.SerializedName;

/**
 * 배달원 실시간 위치(고객용). 서버 GET /orders/{id}/rider-location 응답.
 * 라이더가 해킹커넥트에서 보고한 좌표 — 서버가 검증하지 않으므로 조작된 값이 그대로 온다.
 */
public class RiderLocationDto {
    @SerializedName("latitude") private double latitude;
    @SerializedName("longitude") private double longitude;
    @SerializedName("accuracy") private Double accuracy;
    @SerializedName("updated_at") private String updatedAt;

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Double getAccuracy() { return accuracy; }
    public String getUpdatedAt() { return updatedAt; }
}
