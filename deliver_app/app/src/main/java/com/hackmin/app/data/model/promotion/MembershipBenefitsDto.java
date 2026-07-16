package com.hackmin.app.data.model.promotion;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * GET /membership/benefits 응답.
 * 백엔드 실제 포맷: { "benefits": [{ "title", "description" }], "price": 4900 }
 * (기존 DTO는 benefits를 List&lt;String&gt;로 잘못 정의해 호출 시 크래시났음 — 실제 계약에 맞춰 수정)
 */
public class MembershipBenefitsDto {

    @SerializedName("benefits")
    private List<Benefit> benefits;

    @SerializedName("price")
    private int price;

    public List<Benefit> getBenefits() { return benefits; }
    public int getPrice() { return price; }

    public static class Benefit {
        @SerializedName("title") private String title;
        @SerializedName("description") private String description;

        public String getTitle() { return title; }
        public String getDescription() { return description; }
    }
}
