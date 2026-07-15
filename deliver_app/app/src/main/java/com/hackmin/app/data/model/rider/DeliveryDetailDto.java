package com.hackmin.app.data.model.rider;

import com.google.gson.annotations.SerializedName;

public class DeliveryDetailDto {
    @SerializedName("id") private long id;
    @SerializedName("order") private long order;
    @SerializedName("order_number") private String orderNumber;
    @SerializedName("status") private String status;
    @SerializedName("customer") private String customer;
    @SerializedName("phone") private String phone;
    @SerializedName("address") private String address;
    @SerializedName("address_detail") private String addressDetail;
    @SerializedName("request_note") private String requestNote;
    @SerializedName("assigned_at") private String assignedAt;
    @SerializedName("completed_at") private String completedAt;

    public long getId() { return id; }
    public long getOrder() { return order; }
    public String getOrderNumber() { return orderNumber; }
    public String getStatus() { return status; }
    public String getCustomer() { return customer; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getAddressDetail() { return addressDetail; }
    public String getRequestNote() { return requestNote; }
    public String getAssignedAt() { return assignedAt; }
    public String getCompletedAt() { return completedAt; }
}
