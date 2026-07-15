package com.hackmin.app.data.model.owner;

import com.google.gson.annotations.SerializedName;

public class RejectOrderRequest {
    @SerializedName("reason") private String reason;

    public RejectOrderRequest(String reason) {
        this.reason = reason;
    }
}
