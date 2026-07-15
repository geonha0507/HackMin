package com.hackmin.app.data.model.cart;

import com.google.gson.annotations.SerializedName;

public class UpdateCartItemRequest {
    @SerializedName("quantity") private int quantity;

    public UpdateCartItemRequest(int quantity) {
        this.quantity = quantity;
    }
}
