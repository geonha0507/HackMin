package com.hackmin.app.data.model.restaurant;

import com.google.gson.annotations.SerializedName;

/**
 * GET /restaurants (검색/목록) 결과의 경량 모델.
 * 백엔드 RestaurantListSerializer 대응:
 * id, name, cuisine_type, rating, min_order_amount, delivery_fee, is_open, latitude, longitude
 */
public class RestaurantSummaryDto {

    @SerializedName("id")
    private long id;

    @SerializedName("name")
    private String name;

    // 음식 종류(콤마 구분 복수 가능): "한식,분식"
    @SerializedName("cuisine_type")
    private String cuisineType;

    @SerializedName("rating")
    private double rating;

    // 음식점 대표 이미지 URL(절대/상대). 백엔드에 필드가 아직 없으면 null.
    @SerializedName("image")
    private String image;

    @SerializedName("min_order_amount")
    private long minOrderAmount;

    @SerializedName("delivery_fee")
    private long deliveryFee;

    @SerializedName("is_open")
    private boolean open;

    @SerializedName("latitude")
    private Double latitude; // nullable

    @SerializedName("longitude")
    private Double longitude; // nullable

    public long getId() { return id; }
    public String getName() { return name; }
    public String getCuisineType() { return cuisineType; }
    public double getRating() { return rating; }
    public String getImage() { return image; }
    public long getMinOrderAmount() { return minOrderAmount; }
    public long getDeliveryFee() { return deliveryFee; }
    public boolean isOpen() { return open; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
}
