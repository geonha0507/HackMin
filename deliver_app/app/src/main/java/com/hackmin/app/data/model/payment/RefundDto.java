package com.hackmin.app.data.model.payment;

import com.google.gson.annotations.SerializedName;

public class RefundDto {
    @SerializedName("id") private long id;
    @SerializedName("payment") private long payment;
    @SerializedName("amount") private int amount;
    @SerializedName("reason") private String reason;
    @SerializedName("status") private String status;
    @SerializedName("created_at") private String createdAt;

    public long getId() { return id; }
    public long getPayment() { return payment; }
    public int getAmount() { return amount; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
}
