package com.hackmin.app.data.model.payment;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class PaymentDto {
    @SerializedName("id") private long id;
    @SerializedName("order") private long order;
    @SerializedName("method") private String method;
    @SerializedName("amount") private int amount;
    @SerializedName("status") private String status;
    @SerializedName("transaction_id") private String transactionId;
    @SerializedName("refunds") private List<RefundDto> refunds;
    @SerializedName("created_at") private String createdAt;

    public long getId() { return id; }
    public long getOrder() { return order; }
    public String getMethod() { return method; }
    public int getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getTransactionId() { return transactionId; }
    public List<RefundDto> getRefunds() { return refunds; }
    public String getCreatedAt() { return createdAt; }
}
