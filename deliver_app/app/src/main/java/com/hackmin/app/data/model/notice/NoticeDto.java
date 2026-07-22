package com.hackmin.app.data.model.notice;

import com.google.gson.annotations.SerializedName;

/**
 * 공지사항 DTO. 백엔드 NoticeSerializer 대응:
 * id, title, content, image, is_pinned, created_at, updated_at
 * (GET /api/v1/notices, GET /api/v1/notices/{id})
 */
public class NoticeDto {

    @SerializedName("id")
    private long id;

    @SerializedName("title")
    private String title;

    @SerializedName("content")
    private String content;

    @SerializedName("image")
    private String image; // nullable

    @SerializedName("is_pinned")
    private boolean pinned;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("updated_at")
    private String updatedAt;

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getImage() { return image; }
    public boolean isPinned() { return pinned; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
}
