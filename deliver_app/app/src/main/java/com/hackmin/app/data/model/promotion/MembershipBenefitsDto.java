package com.hackmin.app.data.model.promotion;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class MembershipBenefitsDto {
    @SerializedName("plan") private String plan;
    @SerializedName("benefits") private List<String> benefits;

    public String getPlan() { return plan; }
    public List<String> getBenefits() { return benefits; }
}
