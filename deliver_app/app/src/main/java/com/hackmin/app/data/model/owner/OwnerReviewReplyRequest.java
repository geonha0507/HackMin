package com.hackmin.app.data.model.owner;

import com.google.gson.annotations.SerializedName;

public class OwnerReviewReplyRequest {
    @SerializedName("content") private String content;

    public OwnerReviewReplyRequest(String content) {
        this.content = content;
    }
}
