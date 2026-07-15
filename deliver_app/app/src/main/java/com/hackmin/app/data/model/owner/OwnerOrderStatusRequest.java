package com.hackmin.app.data.model.owner;

import com.google.gson.annotations.SerializedName;

public class OwnerOrderStatusRequest {
    @SerializedName("status") private String status;

    public OwnerOrderStatusRequest(String status) {
        this.status = status;
    }
}
