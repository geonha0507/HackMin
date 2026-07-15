package com.hackmin.app.data.model.review;

import com.google.gson.annotations.SerializedName;

public class ReviewImageDto {
    @SerializedName("id") private long id;
    @SerializedName("image") private String image;
    @SerializedName("created_at") private String createdAt;

    public long getId() { return id; }
    public String getImage() { return image; }
    public String getCreatedAt() { return createdAt; }
}
