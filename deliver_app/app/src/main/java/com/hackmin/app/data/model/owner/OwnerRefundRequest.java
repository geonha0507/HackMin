package com.hackmin.app.data.model.owner;

import com.google.gson.annotations.SerializedName;

public class OwnerRefundRequest {
    @SerializedName("amount") private int amount;
    @SerializedName("reason") private String reason;

    public OwnerRefundRequest(int amount, String reason) {
        this.amount = amount;
        this.reason = reason;
    }
}
