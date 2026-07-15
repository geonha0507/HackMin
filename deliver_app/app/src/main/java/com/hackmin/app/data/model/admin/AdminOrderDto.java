package com.hackmin.app.data.model.admin;

import com.hackmin.app.data.model.order.OrderItemDto;
import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AdminOrderDto {
    @SerializedName("id") private long id;
    @SerializedName("order_number") private String orderNumber;
    @SerializedName("user") private long user;
    @SerializedName("restaurant") private Long restaurant;
    @SerializedName("status") private String status;
    @SerializedName("total") private int total;
    @SerializedName("items") private List<OrderItemDto> items;
    @SerializedName("created_at") private String createdAt;

    public long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public long getUser() { return user; }
    public Long getRestaurant() { return restaurant; }
    public String getStatus() { return status; }
    public int getTotal() { return total; }
    public List<OrderItemDto> getItems() { return items; }
    public String getCreatedAt() { return createdAt; }
}
