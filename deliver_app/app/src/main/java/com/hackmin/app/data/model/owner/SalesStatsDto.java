package com.hackmin.app.data.model.owner;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class SalesStatsDto {
    @SerializedName("daily") private List<DailyStat> daily;

    public List<DailyStat> getDaily() { return daily; }

    public static class DailyStat {
        @SerializedName("date") private String date;
        @SerializedName("revenue") private long revenue;
        @SerializedName("order_count") private int orderCount;

        public String getDate() { return date; }
        public long getRevenue() { return revenue; }
        public int getOrderCount() { return orderCount; }
    }
}
