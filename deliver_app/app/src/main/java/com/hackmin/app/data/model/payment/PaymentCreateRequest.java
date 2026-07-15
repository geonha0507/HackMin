package com.hackmin.app.data.model.payment;

import com.google.gson.annotations.SerializedName;

public class PaymentCreateRequest {
    @SerializedName("order") private long order;
    @SerializedName("method") private String method;
    @SerializedName("amount") private Integer amount;

    public PaymentCreateRequest(long order, String method) {
        this.order = order;
        this.method = method;
    }

    public PaymentCreateRequest(long order, String method, int amount) {
        this.order = order;
        this.method = method;
        this.amount = amount;
    }
}
