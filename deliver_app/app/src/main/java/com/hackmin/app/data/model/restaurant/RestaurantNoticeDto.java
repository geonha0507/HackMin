package com.hackmin.app.data.model.restaurant;

import com.google.gson.annotations.SerializedName;

/**
 * 매장 공지사항 DTO. 백엔드 RestaurantNoticeSerializer 대응:
 * id, restaurant, title, content, created_at, updated_at
 * (GET /api/v1/restaurants/{id}/notices)
 */
public class RestaurantNoticeDto {

    @SerializedName("id")
    private long id;

    @SerializedName("restaurant")
    private long restaurant;

    @SerializedName("title")
    private String title;

    @SerializedName("content")
    private String content;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("updated_at")
    private String updatedAt;

    public long getId() { return id; }
    public long getRestaurant() { return restaurant; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
}
