package com.hackmin.app.data.model.promotion;

import com.google.gson.annotations.SerializedName;

public class MembershipPaymentDto {
    @SerializedName("id") private long id;
    @SerializedName("amount") private int amount;
    @SerializedName("paid_at") private String paidAt;

    public long getId() { return id; }
    public int getAmount() { return amount; }
    public String getPaidAt() { return paidAt; }
}
