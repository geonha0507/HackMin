package com.hackmin.app.data.model.review;

import com.google.gson.annotations.SerializedName;

public class ReviewCreateRequest {
    @SerializedName("restaurant") private long restaurant;
    @SerializedName("order") private Long order;
    @SerializedName("rating") private int rating;
    @SerializedName("content") private String content;

    public ReviewCreateRequest(long restaurant, Long order, int rating, String content) {
        this.restaurant = restaurant;
        this.order = order;
        this.rating = rating;
        this.content = content;
    }
}
