package com.hackmin.app.data.model.order;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class OrderDto {
    @SerializedName("id") private long id;
    @SerializedName("order_number") private String orderNumber;
    @SerializedName("restaurant") private Long restaurant;
    @SerializedName("status") private String status;
    @SerializedName("subtotal") private int subtotal;
    @SerializedName("delivery_fee") private int deliveryFee;
    @SerializedName("discount") private int discount;
    @SerializedName("total") private int total;
    @SerializedName("address") private String address;
    @SerializedName("address_detail") private String addressDetail;
    @SerializedName("request_note") private String requestNote;
    @SerializedName("items") private List<OrderItemDto> items;
    @SerializedName("created_at") private String createdAt;
    @SerializedName("updated_at") private String updatedAt;

    public long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public Long getRestaurant() { return restaurant; }
    public String getStatus() { return status; }
    public int getSubtotal() { return subtotal; }
    public int getDeliveryFee() { return deliveryFee; }
    public int getDiscount() { return discount; }
    public int getTotal() { return total; }
    public String getAddress() { return address; }
    public String getAddressDetail() { return addressDetail; }
    public String getRequestNote() { return requestNote; }
    public List<OrderItemDto> getItems() { return items; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
}
