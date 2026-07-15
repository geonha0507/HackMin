package com.hackmin.app.data.model.restaurant;

import com.google.gson.annotations.SerializedName;

/**
 * GET /restaurants/{id} 상세 모델.
 * 백엔드 RestaurantDetailSerializer 대응:
 * id, name, cuisine_type, description, phone, address, latitude, longitude,
 * min_order_amount, delivery_fee, rating, is_open, created_at
 */
public class RestaurantDetailDto {

    @SerializedName("id")
    private long id;

    @SerializedName("name")
    private String name;

    @SerializedName("cuisine_type")
    private String cuisineType;

    @SerializedName("description")
    private String description;

    @SerializedName("phone")
    private String phone;

    @SerializedName("address")
    private String address;

    @SerializedName("latitude")
    private Double latitude;

    @SerializedName("longitude")
    private Double longitude;

    @SerializedName("min_order_amount")
    private long minOrderAmount;

    @SerializedName("delivery_fee")
    private long deliveryFee;

    @SerializedName("rating")
    private double rating;

    @SerializedName("is_open")
    private boolean open;

    @SerializedName("created_at")
    private String createdAt;

    public long getId() { return id; }
    public String getName() { return name; }
    public String getCuisineType() { return cuisineType; }
    public String getDescription() { return description; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public long getMinOrderAmount() { return minOrderAmount; }
    public long getDeliveryFee() { return deliveryFee; }
    public double getRating() { return rating; }
    public boolean isOpen() { return open; }
    public String getCreatedAt() { return createdAt; }
}
