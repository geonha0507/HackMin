package com.hackmin.app.data.api;

import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.inquiry.InquiryCreateRequest;
import com.hackmin.app.data.model.inquiry.InquiryDto;
import com.hackmin.app.data.model.inquiry.InquiryImageDto;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;

/**
 * 1:1 문의 API (/api/v1/inquiries)
 */
public interface InquiryApi {

    /** 내가 작성한 1:1 문의 목록 조회 (작성일 오름차순) */
    @GET("inquiries")
    Call<PagedResponse<InquiryDto>> getInquiries();

    /** 1:1 문의 작성 */
    @POST("inquiries")
    Call<InquiryDto> createInquiry(@Body InquiryCreateRequest request);

    /** 1:1 문의 상세 조회 */
    @GET("inquiries/{id}")
    Call<InquiryDto> getInquiry(@Path("id") long inquiryId);

    /** 1:1 문의 수정 */
    @PUT("inquiries/{id}")
    Call<InquiryDto> updateInquiry(@Path("id") long inquiryId, @Body InquiryCreateRequest request);

    /** 1:1 문의 삭제 */
    @DELETE("inquiries/{id}")
    Call<Void> deleteInquiry(@Path("id") long inquiryId);

    /** 1:1 문의 이미지 업로드 */
    @Multipart
    @POST("inquiries/{id}/images")
    Call<InquiryImageDto> uploadInquiryImage(@Path("id") long inquiryId, @Part MultipartBody.Part image);
}
