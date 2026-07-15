package com.hackmin.app.data.model.common;

import com.google.gson.annotations.SerializedName;

public class MessageResponse {
    @SerializedName("detail")
    private String detail;

    public String getDetail() { return detail; }
}
