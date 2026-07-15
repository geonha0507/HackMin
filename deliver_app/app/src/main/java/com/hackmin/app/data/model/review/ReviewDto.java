package com.hackmin.app.data.model.review;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ReviewDto {
    @SerializedName("id") private long id;
    @SerializedName("restaurant") private long restaurant;
    @SerializedName("order") private Long order;
    @SerializedName("author") private String author;
    @SerializedName("rating") private int rating;
    @SerializedName("content") private String content;
    @SerializedName("images") private List<ReviewImageDto> images;
    @SerializedName("reply") private ReviewReplyDto reply;
    @SerializedName("created_at") private String createdAt;
    @SerializedName("updated_at") private String updatedAt;

    public long getId() { return id; }
    public long getRestaurant() { return restaurant; }
    public Long getOrder() { return order; }
    public String getAuthor() { return author; }
    public int getRating() { return rating; }
    public String getContent() { return content; }
    public List<ReviewImageDto> getImages() { return images; }
    public ReviewReplyDto getReply() { return reply; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
}
