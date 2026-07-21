package com.hackmin.app.data.model.review;

import com.google.gson.annotations.SerializedName;

public class ReviewCreateRequest {
    @SerializedName("restaurant") private long restaurant;
    @SerializedName("order") private Long order;
    // 0.5 단위 별점 지원. 정수 별점은 Integer로 담아 "5"로 직렬화(정수만 받는 서버와 호환),
    // 반칸 별점은 Double로 담아 "4.5"로 직렬화된다.
    @SerializedName("rating") private Number rating;
    @SerializedName("content") private String content;

    public ReviewCreateRequest(long restaurant, Long order, Number rating, String content) {
        this.restaurant = restaurant;
        this.order = order;
        this.rating = rating;
        this.content = content;
    }
}
