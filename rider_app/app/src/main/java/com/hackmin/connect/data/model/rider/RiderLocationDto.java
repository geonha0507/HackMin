package com.hackmin.connect.data.model.rider;

import com.google.gson.annotations.SerializedName;

/**
 * 라이더 위치 전송/응답 DTO. 서버 /rider/location 의 RiderLocationSerializer 와 대응.
 * accuracy(오차 m)는 선택 — 없으면 null 로 두어 직렬화에서 빠진다.
 */
public class RiderLocationDto {
    @SerializedName("latitude") private double latitude;
    @SerializedName("longitude") private double longitude;
    @SerializedName("accuracy") private Double accuracy;
    @SerializedName("updated_at") private String updatedAt;

    public RiderLocationDto(double latitude, double longitude, Double accuracy) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
    }

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Double getAccuracy() { return accuracy; }
    public String getUpdatedAt() { return updatedAt; }
}
