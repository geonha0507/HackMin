package com.hackmin.app.data.model.promotion;

import com.google.gson.annotations.SerializedName;

public class MembershipDto {
    @SerializedName("id") private long id;
    @SerializedName("plan") private String plan;
    @SerializedName("status") private String status;
    @SerializedName("started_at") private String startedAt;
    @SerializedName("cancelled_at") private String cancelledAt;

    public long getId() { return id; }
    public String getPlan() { return plan; }
    public String getStatus() { return status; }
    public String getStartedAt() { return startedAt; }
    public String getCancelledAt() { return cancelledAt; }
}
