package com.hackmin.app.data.model.chat;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ChatHistoryResponse {
    @SerializedName("session_id") private Long sessionId;
    @SerializedName("results") private List<ChatMessageDto> results;

    public Long getSessionId() { return sessionId; }
    public List<ChatMessageDto> getResults() { return results; }
}
