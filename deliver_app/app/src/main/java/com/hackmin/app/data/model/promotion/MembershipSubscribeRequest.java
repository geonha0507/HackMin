package com.hackmin.app.data.model.promotion;

import com.google.gson.annotations.SerializedName;

public class MembershipSubscribeRequest {
    @SerializedName("plan") private String plan;

    public MembershipSubscribeRequest(String plan) {
        this.plan = plan;
    }
}
