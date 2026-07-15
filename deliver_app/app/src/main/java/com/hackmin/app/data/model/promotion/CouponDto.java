package com.hackmin.app.data.model.promotion;

import com.google.gson.annotations.SerializedName;

public class CouponDto {
    @SerializedName("id") private long id;
    @SerializedName("name") private String name;
    @SerializedName("discount_type") private String discountType;
    @SerializedName("discount_value") private int discountValue;
    @SerializedName("min_order_amount") private int minOrderAmount;
    @SerializedName("valid_until") private String validUntil;

    public long getId() { return id; }
    public String getName() { return name; }
    public String getDiscountType() { return discountType; }
    public int getDiscountValue() { return discountValue; }
    public int getMinOrderAmount() { return minOrderAmount; }
    public String getValidUntil() { return validUntil; }
}
