package com.hackmin.app.data.api;

import java.util.Map;
import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * Maps to README Section 8 — 고객 — 리뷰 /reviews
 */
public interface ReviewApi {

    /** 🎯 vulnerable: validation bypass · Stored XSS */
    @POST("reviews")
    Call<Object> createReview(@Body Map<String, Object> review);

    /** 🎯 vulnerable: IDOR */
    @PUT("reviews/{id}")
    Call<Object> updateReview(@Path("id") long reviewId, @Body Map<String, Object> review);

    @DELETE("reviews/{id}")
    Call<Void> deleteReview(@Path("id") long reviewId);

    /** 🎯 vulnerable: unlimited upload */
    @Multipart
    @POST("reviews/{id}/images")
    Call<Object> uploadReviewImage(@Path("id") long reviewId, @Part MultipartBody.Part image);
}
