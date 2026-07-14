package com.hackmin.app.model.dto.restaurant;

import com.google.gson.annotations.SerializedName;

/** Lightweight shape for GET /restaurants (search/list results). */
public class RestaurantSummaryDto {

    @SerializedName("id")
    private long id;

    @SerializedName("name")
    private String name;

    @SerializedName("category")
    private String category;

    @SerializedName("thumbnail_url")
    private String thumbnailUrl;

    @SerializedName("rating")
    private double rating;

    @SerializedName("review_count")
    private int reviewCount;

    @SerializedName("delivery_fee")
    private long deliveryFee;

    @SerializedName("min_order_amount")
    private long minOrderAmount;

    @SerializedName("estimated_delivery_minutes")
    private int estimatedDeliveryMinutes;

    @SerializedName("distance_meters")
    private Double distanceMeters; // nullable when no location context

    public long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public double getRating() { return rating; }
    public int getReviewCount() { return reviewCount; }
    public long getDeliveryFee() { return deliveryFee; }
    public long getMinOrderAmount() { return minOrderAmount; }
    public int getEstimatedDeliveryMinutes() { return estimatedDeliveryMinutes; }
    public Double getDistanceMeters() { return distanceMeters; }
}
