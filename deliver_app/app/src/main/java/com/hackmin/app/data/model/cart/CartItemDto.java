package com.hackmin.app.data.model.cart;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class CartItemDto {
    @SerializedName("id") private long id;
    @SerializedName("menu") private long menu;
    @SerializedName("menu_name") private String menuName;
    @SerializedName("quantity") private int quantity;
    @SerializedName("options") private List<Integer> options;
    @SerializedName("unit_price") private int unitPrice;
    @SerializedName("line_total") private int lineTotal;

    public long getId() { return id; }
    public long getMenu() { return menu; }
    public String getMenuName() { return menuName; }
    public int getQuantity() { return quantity; }
    public List<Integer> getOptions() { return options; }
    public int getUnitPrice() { return unitPrice; }
    public int getLineTotal() { return lineTotal; }
}
