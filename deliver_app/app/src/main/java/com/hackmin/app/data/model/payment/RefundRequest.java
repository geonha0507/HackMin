package com.hackmin.app.data.model.payment;

import com.google.gson.annotations.SerializedName;

public class RefundRequest {
    @SerializedName("amount") private int amount;
    @SerializedName("reason") private String reason;

    public RefundRequest(int amount, String reason) {
        this.amount = amount;
        this.reason = reason;
    }
}
