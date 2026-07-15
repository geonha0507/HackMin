package com.hackmin.app.data.model.review;

import com.google.gson.annotations.SerializedName;

public class ReviewReplyDto {
    @SerializedName("id") private long id;
    @SerializedName("content") private String content;
    @SerializedName("created_at") private String createdAt;

    public long getId() { return id; }
    public String getContent() { return content; }
    public String getCreatedAt() { return createdAt; }
}
