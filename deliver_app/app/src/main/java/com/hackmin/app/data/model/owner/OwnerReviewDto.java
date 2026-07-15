package com.hackmin.app.data.model.owner;

import com.hackmin.app.data.model.review.ReviewReplyDto;
import com.google.gson.annotations.SerializedName;

public class OwnerReviewDto {
    @SerializedName("id") private long id;
    @SerializedName("author") private String author;
    @SerializedName("rating") private int rating;
    @SerializedName("content") private String content;
    @SerializedName("reply") private ReviewReplyDto reply;
    @SerializedName("created_at") private String createdAt;

    public long getId() { return id; }
    public String getAuthor() { return author; }
    public int getRating() { return rating; }
    public String getContent() { return content; }
    public ReviewReplyDto getReply() { return reply; }
    public String getCreatedAt() { return createdAt; }
}
