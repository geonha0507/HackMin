package com.hackmin.app.data.model.promotion;

import com.google.gson.annotations.SerializedName;

public class FavoriteDto {
    @SerializedName("id") private long id;
    @SerializedName("restaurant") private long restaurant;
    @SerializedName("restaurant_name") private String restaurantName;
    @SerializedName("created_at") private String createdAt;

    public long getId() { return id; }
    public long getRestaurant() { return restaurant; }
    public String getRestaurantName() { return restaurantName; }
    public String getCreatedAt() { return createdAt; }
}
