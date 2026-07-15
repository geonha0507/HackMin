package com.hackmin.app.data.model.cart;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AddCartItemRequest {
    @SerializedName("menu") private long menu;
    @SerializedName("quantity") private int quantity;
    @SerializedName("options") private List<Integer> options;

    public AddCartItemRequest(long menu, int quantity, List<Integer> options) {
        this.menu = menu;
        this.quantity = quantity;
        this.options = options;
    }
}
