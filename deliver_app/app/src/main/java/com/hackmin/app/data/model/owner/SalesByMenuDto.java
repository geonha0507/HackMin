package com.hackmin.app.data.model.owner;

import com.google.gson.annotations.SerializedName;

public class SalesByMenuDto {
    @SerializedName("menu_id") private long menuId;
    @SerializedName("menu_name") private String menuName;
    @SerializedName("quantity") private int quantity;
    @SerializedName("revenue") private long revenue;

    public long getMenuId() { return menuId; }
    public String getMenuName() { return menuName; }
    public int getQuantity() { return quantity; }
    public long getRevenue() { return revenue; }
}
