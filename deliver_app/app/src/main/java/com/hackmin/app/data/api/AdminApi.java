package com.hackmin.app.data.api;

import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.admin.*;

import retrofit2.Call;
import retrofit2.http.*;

/**
 * 관리자 API (/api/v1/admin)
 * 인증 필요 (Bearer token, role=admin)
 */
public interface AdminApi {

    /** 사용자 목록 조회 */
    @GET("admin/users")
    Call<AdminUserListResponse> getUsers(@Query("q") String query);

    /** 사용자 상세 조회 */
    @GET("admin/users/{id}")
    Call<AdminUserDto> getUser(@Path("id") long userId);

    /** 회원 탈퇴 처리 */
    @DELETE("admin/users/{id}")
    Call<Void> deleteUser(@Path("id") long userId);

    /** 사용자 상태 변경 (활성/정지) */
    @PUT("admin/users/{id}/status")
    Call<AdminUserDto> updateUserStatus(@Path("id") long userId, @Body AdminUserStatusRequest request);

    /** 사장님 목록 조회 */
    @GET("admin/owners")
    Call<AdminUserListResponse> getOwners(@Query("q") String query);

    /** 사장님 승인/반려 */
    @PUT("admin/owners/{id}/status")
    Call<AdminUserDto> updateOwnerStatus(@Path("id") long ownerId, @Body AdminUserStatusRequest request);

    /** 전체 주문 목록 */
    @GET("admin/orders")
    Call<PagedResponse<AdminOrderDto>> getOrders(
            @Query("status") String status,
            @Query("page") Integer page
    );

    /** 전체 결제 목록 */
    @GET("admin/payments")
    Call<PagedResponse<AdminPaymentDto>> getPayments(@Query("page") Integer page);
}
