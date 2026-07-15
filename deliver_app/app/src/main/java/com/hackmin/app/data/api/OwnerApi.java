package com.hackmin.app.data.api;

import com.hackmin.app.data.model.common.MessageResponse;
import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.owner.*;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * 사장님 API (/api/v1/owner)
 * 인증 필요 (Bearer token, role=owner)
 */
public interface OwnerApi {

    // ── 계정 ──

    /** 사장님 회원가입 */
    @POST("owner/signup")
    Call<OwnerSignupResponse> ownerSignup(@Body OwnerSignupRequest request);

    /** 사장님 프로필 조회 */
    @GET("owner/profile")
    Call<OwnerProfileDto> getProfile();

    /** 사장님 프로필 수정 */
    @PUT("owner/profile")
    Call<OwnerProfileDto> updateProfile(@Body OwnerProfileDto profile);

    /** 사업자등록증 업로드 */
    @Multipart
    @POST("owner/business-license")
    Call<MessageResponse> uploadBusinessLicense(@Part MultipartBody.Part file);

    // ── 상품 (메뉴) ──

    /** 메뉴 목록 조회 */
    @GET("owner/products")
    Call<PagedResponse<ProductDto>> getProducts(@Query("page") Integer page);

    /** 메뉴 등록 */
    @POST("owner/products")
    Call<ProductDto> createProduct(@Body ProductDto product);

    /** 메뉴 상세 조회 */
    @GET("owner/products/{id}")
    Call<ProductDto> getProduct(@Path("id") long productId);

    /** 메뉴 수정 */
    @PUT("owner/products/{id}")
    Call<ProductDto> updateProduct(@Path("id") long productId, @Body ProductDto product);

    /** 메뉴 삭제 */
    @DELETE("owner/products/{id}")
    Call<Void> deleteProduct(@Path("id") long productId);

    /** 메뉴 이미지 업로드 */
    @Multipart
    @POST("owner/products/{id}/image")
    Call<ProductDto> uploadProductImage(@Path("id") long productId, @Part MultipartBody.Part image);

    /** 메뉴 상태 변경 (판매중/품절/숨김) */
    @PUT("owner/products/{id}/status")
    Call<ProductDto> updateProductStatus(@Path("id") long productId, @Body ProductStatusRequest request);

    /** 메뉴 옵션 그룹 관리 */
    @GET("owner/products/{id}/options")
    Call<java.util.List<OptionGroupDto>> getProductOptions(@Path("id") long productId);

    @POST("owner/products/{id}/options")
    Call<OptionGroupDto> createOptionGroup(@Path("id") long productId, @Body OptionGroupDto group);

    /** 옵션 그룹 수정/삭제 */
    @PUT("owner/options/{id}")
    Call<OptionGroupDto> updateOption(@Path("id") long optionId, @Body OptionGroupDto option);

    @DELETE("owner/options/{id}")
    Call<Void> deleteOption(@Path("id") long optionId);

    // ── 카테고리 ──

    @GET("owner/categories")
    Call<java.util.List<CategoryDto>> getCategories();

    @POST("owner/categories")
    Call<CategoryDto> createCategory(@Body CategoryDto category);

    @PUT("owner/categories/{id}")
    Call<CategoryDto> updateCategory(@Path("id") long categoryId, @Body CategoryDto category);

    @DELETE("owner/categories/{id}")
    Call<Void> deleteCategory(@Path("id") long categoryId);

    // ── 주문 관리 ──

    @GET("owner/orders")
    Call<PagedResponse<OwnerOrderDto>> getOrders(
            @Query("status") String status,
            @Query("page") Integer page
    );

    @GET("owner/orders/{id}")
    Call<OwnerOrderDto> getOrder(@Path("id") long orderId);

    @POST("owner/orders/{id}/accept")
    Call<OwnerOrderDto> acceptOrder(@Path("id") long orderId);

    @POST("owner/orders/{id}/reject")
    Call<OwnerOrderDto> rejectOrder(@Path("id") long orderId, @Body RejectOrderRequest request);

    @PUT("owner/orders/{id}/status")
    Call<OwnerOrderDto> updateOrderStatus(@Path("id") long orderId, @Body OwnerOrderStatusRequest request);

    @POST("owner/orders/{id}/cancel")
    Call<OwnerOrderDto> cancelOrder(@Path("id") long orderId);

    // ── 결제 관리 ──

    @GET("owner/payments")
    Call<PagedResponse<OwnerPaymentDto>> getPayments(@Query("page") Integer page);

    @GET("owner/payments/{id}")
    Call<OwnerPaymentDto> getPaymentDetail(@Path("id") long paymentId);

    @POST("owner/payments/{id}/cancel")
    Call<OwnerPaymentDto> cancelPayment(@Path("id") long paymentId);

    @POST("owner/payments/{id}/refund")
    Call<OwnerPaymentDto> refundPayment(@Path("id") long paymentId, @Body OwnerRefundRequest request);

    // ── 매출 ──

    @GET("owner/sales")
    Call<SalesSummaryDto> getSalesSummary(
            @Query("start_date") String startDate,
            @Query("end_date") String endDate
    );

    @GET("owner/sales/by-menu")
    Call<java.util.List<SalesByMenuDto>> getSalesByMenu(
            @Query("start_date") String startDate,
            @Query("end_date") String endDate
    );

    @GET("owner/sales/stats")
    Call<SalesStatsDto> getSalesStats(
            @Query("start_date") String startDate,
            @Query("end_date") String endDate
    );

    // ── 리뷰 관리 ──

    @GET("owner/reviews")
    Call<PagedResponse<OwnerReviewDto>> getReviews(@Query("page") Integer page);

    @POST("owner/reviews/{id}/reply")
    Call<OwnerReviewDto> replyToReview(@Path("id") long reviewId, @Body OwnerReviewReplyRequest request);
}
