package com.hackmin.app.data.model.chat;

import com.google.gson.annotations.SerializedName;

public class ChatMessageDto {
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    @SerializedName("role") private String role;
    @SerializedName("content") private String content;
    @SerializedName("created_at") private String createdAt;

    public ChatMessageDto() {}

    public ChatMessageDto(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() { return role; }
    public String getContent() { return content; }
    public String getCreatedAt() { return createdAt; }

    public boolean isUser() { return ROLE_USER.equals(role); }
}
