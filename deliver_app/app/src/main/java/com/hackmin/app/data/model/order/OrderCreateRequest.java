package com.hackmin.app.data.model.order;

import com.google.gson.annotations.SerializedName;

public class OrderCreateRequest {
    @SerializedName("address") private String address;
    @SerializedName("address_detail") private String addressDetail;
    @SerializedName("request_note") private String requestNote;

    public OrderCreateRequest(String address, String addressDetail, String requestNote) {
        this.address = address;
        this.addressDetail = addressDetail;
        this.requestNote = requestNote;
    }
}
