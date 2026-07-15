package com.hackmin.app.data.model.owner;

import com.google.gson.annotations.SerializedName;

public class SalesSummaryDto {
    @SerializedName("total_revenue") private long totalRevenue;
    @SerializedName("total_orders") private int totalOrders;
    @SerializedName("period_start") private String periodStart;
    @SerializedName("period_end") private String periodEnd;

    public long getTotalRevenue() { return totalRevenue; }
    public int getTotalOrders() { return totalOrders; }
    public String getPeriodStart() { return periodStart; }
    public String getPeriodEnd() { return periodEnd; }
}
