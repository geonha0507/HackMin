package com.hackmin.connect.data.model.rider;

import com.google.gson.annotations.SerializedName;

public class DeliveryDto {
    @SerializedName("id") private long id;
    @SerializedName("order") private long order;
    @SerializedName("order_number") private String orderNumber;
    @SerializedName("restaurant") private String restaurant;
    @SerializedName("total") private int total;
    @SerializedName("status") private String status;
    @SerializedName("assigned_at") private String assignedAt;

    public long getId() { return id; }
    public long getOrder() { return order; }
    public String getOrderNumber() { return orderNumber; }
    public String getRestaurant() { return restaurant; }
    public int getTotal() { return total; }
    public String getStatus() { return status; }
    public String getAssignedAt() { return assignedAt; }
}
