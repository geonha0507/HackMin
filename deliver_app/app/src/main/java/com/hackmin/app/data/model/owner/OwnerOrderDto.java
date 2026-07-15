package com.hackmin.app.data.model.owner;

import com.hackmin.app.data.model.order.OrderItemDto;
import com.google.gson.annotations.SerializedName;
import java.util.List;

public class OwnerOrderDto {
    @SerializedName("id") private long id;
    @SerializedName("order_number") private String orderNumber;
    @SerializedName("status") private String status;
    @SerializedName("total") private int total;
    @SerializedName("items") private List<OrderItemDto> items;
    @SerializedName("address") private String address;
    @SerializedName("request_note") private String requestNote;
    @SerializedName("created_at") private String createdAt;

    public long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public String getStatus() { return status; }
    public int getTotal() { return total; }
    public List<OrderItemDto> getItems() { return items; }
    public String getAddress() { return address; }
    public String getRequestNote() { return requestNote; }
    public String getCreatedAt() { return createdAt; }
}
