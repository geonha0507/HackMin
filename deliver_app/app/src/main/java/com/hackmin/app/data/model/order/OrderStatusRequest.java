package com.hackmin.app.data.model.order;

import com.google.gson.annotations.SerializedName;

public class OrderStatusRequest {
    @SerializedName("status") private String status;

    public OrderStatusRequest(String status) {
        this.status = status;
    }
}
