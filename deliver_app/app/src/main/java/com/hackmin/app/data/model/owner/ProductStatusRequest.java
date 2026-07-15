package com.hackmin.app.data.model.owner;

import com.google.gson.annotations.SerializedName;

public class ProductStatusRequest {
    @SerializedName("status") private String status;

    public ProductStatusRequest(String status) {
        this.status = status;
    }
}
