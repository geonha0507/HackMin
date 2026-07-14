package com.hackmin.app.data.model.restaurant;

import com.google.gson.annotations.SerializedName;

/** Full detail shape for GET /restaurants/{id}. */
public class RestaurantDetailDto {

    @SerializedName("id")
    private long id;

    @SerializedName("name")
    private String name;

    @SerializedName("category")
    private String category;

    @SerializedName("description")
    private String description;

    @SerializedName("address")
    private String address;

    @SerializedName("phone")
    private String phone;

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

    @SerializedName("open_now")
    private boolean openNow;

    public long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public double getRating() { return rating; }
    public int getReviewCount() { return reviewCount; }
    public long getDeliveryFee() { return deliveryFee; }
    public long getMinOrderAmount() { return minOrderAmount; }
    public int getEstimatedDeliveryMinutes() { return estimatedDeliveryMinutes; }
    public boolean isOpenNow() { return openNow; }
}
