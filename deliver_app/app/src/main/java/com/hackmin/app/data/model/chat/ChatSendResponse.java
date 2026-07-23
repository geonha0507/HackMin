package com.hackmin.app.data.model.chat;

import com.google.gson.annotations.SerializedName;

public class ChatSendResponse {
    @SerializedName("session_id") private long sessionId;
    @SerializedName("reply") private String reply;

    public long getSessionId() { return sessionId; }
    public String getReply() { return reply; }
}
