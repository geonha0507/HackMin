package com.hackmin.app.data.model.promotion;

import com.google.gson.annotations.SerializedName;

public class RegisterCouponRequest {
    @SerializedName("code") private String code;

    public RegisterCouponRequest(String code) {
        this.code = code;
    }
}
