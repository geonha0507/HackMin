package com.hackmin.app.data.model.chat;

import com.google.gson.annotations.SerializedName;

public class ChatSendRequest {
    @SerializedName("message") private final String message;

    public ChatSendRequest(String message) {
        this.message = message;
    }
}
