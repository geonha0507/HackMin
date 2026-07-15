package com.hackmin.app.data.model.promotion;

import com.google.gson.annotations.SerializedName;

public class UserCouponDto {
    @SerializedName("id") private long id;
    @SerializedName("coupon") private CouponDto coupon;
    @SerializedName("is_used") private boolean isUsed;
    @SerializedName("downloaded_at") private String downloadedAt;
    @SerializedName("used_at") private String usedAt;

    public long getId() { return id; }
    public CouponDto getCoupon() { return coupon; }
    public boolean isUsed() { return isUsed; }
    public String getDownloadedAt() { return downloadedAt; }
    public String getUsedAt() { return usedAt; }
}
