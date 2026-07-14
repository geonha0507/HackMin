package com.hackmin.app.model.dto.restaurant;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class RestaurantReviewDto {

    @SerializedName("id")
    private long id;

    @SerializedName("author_name")
    private String authorName;

    @SerializedName("rating")
    private int rating;

    @SerializedName("content")
    private String content;

    @SerializedName("image_urls")
    private List<String> imageUrls;

    @SerializedName("owner_reply")
    private String ownerReply; // nullable

    @SerializedName("created_at")
    private String createdAt;

    public long getId() { return id; }
    public String getAuthorName() { return authorName; }
    public int getRating() { return rating; }
    public String getContent() { return content; }
    public List<String> getImageUrls() { return imageUrls; }
    public String getOwnerReply() { return ownerReply; }
    public String getCreatedAt() { return createdAt; }
}
