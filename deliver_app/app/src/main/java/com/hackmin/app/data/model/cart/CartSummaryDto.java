package com.hackmin.app.data.model.cart;

import com.google.gson.annotations.SerializedName;

public class CartSummaryDto {
    @SerializedName("subtotal") private int subtotal;
    @SerializedName("delivery_fee") private int deliveryFee;
    @SerializedName("discount") private int discount;
    @SerializedName("total") private int total;

    public int getSubtotal() { return subtotal; }
    public int getDeliveryFee() { return deliveryFee; }
    public int getDiscount() { return discount; }
    public int getTotal() { return total; }
}
