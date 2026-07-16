package com.hackmin.app.data.model.cart;

import com.google.gson.annotations.SerializedName;

public class ApplyCouponRequest {
    // 백엔드 apply_coupon 은 'code' 를 읽는다(‘coupon_code’ 아님).
    @SerializedName("code") private String code;

    public ApplyCouponRequest(String code) {
        this.code = code;
    }
}
