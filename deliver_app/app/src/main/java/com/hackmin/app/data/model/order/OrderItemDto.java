package com.hackmin.app.data.model.order;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class OrderItemDto {
    @SerializedName("id") private long id;
    @SerializedName("menu") private Long menu;
    @SerializedName("menu_name") private String menuName;
    @SerializedName("unit_price") private int unitPrice;
    @SerializedName("quantity") private int quantity;
    @SerializedName("options") private List<Object> options;
    @SerializedName("line_total") private int lineTotal;

    public long getId() { return id; }
    public Long getMenu() { return menu; }
    public String getMenuName() { return menuName; }
    public int getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
    public List<Object> getOptions() { return options; }
    public int getLineTotal() { return lineTotal; }
}
