package com.hackmin.app.data.api;

import com.hackmin.app.data.model.common.MessageResponse;
import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.review.*;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * 리뷰 API (/api/v1/reviews, /api/v1/me/reviews)
 * 인증 필요 (Bearer token)
 */
public interface ReviewApi {

    /** 리뷰 작성 */
    @POST("reviews")
    Call<ReviewDto> createReview(@Body ReviewCreateRequest request);

    /** 리뷰 상세 조회 */
    @GET("reviews/{id}")
    Call<ReviewDto> getReview(@Path("id") long reviewId);

    /** 리뷰 수정 */
    @PUT("reviews/{id}")
    Call<ReviewDto> updateReview(@Path("id") long reviewId, @Body ReviewCreateRequest request);

    /** 리뷰 삭제 */
    @DELETE("reviews/{id}")
    Call<Void> deleteReview(@Path("id") long reviewId);

    /** 리뷰 이미지 업로드 */
    @Multipart
    @POST("reviews/{id}/images")
    Call<ReviewImageDto> uploadReviewImage(
            @Path("id") long reviewId,
            @Part MultipartBody.Part image
    );

    /** 내 리뷰 목록 */
    @GET("me/reviews")
    Call<PagedResponse<ReviewDto>> getMyReviews(@Query("page") Integer page);
}
