package com.hackmin.app.data.model.inquiry;

import com.google.gson.annotations.SerializedName;

public class InquiryCreateRequest {

    @SerializedName("title") private final String title;
    @SerializedName("category") private final String category;
    @SerializedName("content") private final String content;

    public InquiryCreateRequest(String title, String category, String content) {
        this.title = title;
        this.category = category;
        this.content = content;
    }
}
