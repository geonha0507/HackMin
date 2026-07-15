package com.hackmin.app.data.model.cart;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class CartDto {
    @SerializedName("id") private long id;
    @SerializedName("restaurant") private Long restaurant;
    @SerializedName("coupon") private Long coupon;
    @SerializedName("items") private List<CartItemDto> items;
    @SerializedName("updated_at") private String updatedAt;

    public long getId() { return id; }
    public Long getRestaurant() { return restaurant; }
    public Long getCoupon() { return coupon; }
    public List<CartItemDto> getItems() { return items; }
    public String getUpdatedAt() { return updatedAt; }
}
