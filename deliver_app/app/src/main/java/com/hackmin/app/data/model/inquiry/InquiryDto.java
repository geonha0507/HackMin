package com.hackmin.app.data.model.inquiry;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class InquiryDto {

    @SerializedName("id") private long id;
    @SerializedName("title") private String title;
    @SerializedName("category") private String category;
    @SerializedName("category_display") private String categoryDisplay;
    @SerializedName("content") private String content;
    @SerializedName("author") private String author;
    @SerializedName("images") private List<InquiryImageDto> images;
    @SerializedName("created_at") private String createdAt;
    @SerializedName("updated_at") private String updatedAt;

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getCategoryDisplay() { return categoryDisplay; }
    public String getContent() { return content; }
    public String getAuthor() { return author; }
    public List<InquiryImageDto> getImages() { return images; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
}
