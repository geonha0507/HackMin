package com.hackmin.connect.data.model.rider;

import com.google.gson.annotations.SerializedName;

public class DeliveryStatusRequest {
    @SerializedName("status") private String status;

    public DeliveryStatusRequest(String status) {
        this.status = status;
    }
}
