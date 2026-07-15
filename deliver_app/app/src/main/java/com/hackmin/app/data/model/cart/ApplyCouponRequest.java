package com.hackmin.app.data.model.cart;

import com.google.gson.annotations.SerializedName;

public class ApplyCouponRequest {
    @SerializedName("coupon_code") private String couponCode;

    public ApplyCouponRequest(String couponCode) {
        this.couponCode = couponCode;
    }
}
