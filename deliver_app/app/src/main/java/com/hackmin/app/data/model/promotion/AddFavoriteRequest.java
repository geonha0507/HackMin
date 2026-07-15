package com.hackmin.app.data.model.promotion;

import com.google.gson.annotations.SerializedName;

public class AddFavoriteRequest {
    @SerializedName("restaurant") private long restaurant;

    public AddFavoriteRequest(long restaurant) {
        this.restaurant = restaurant;
    }
}
