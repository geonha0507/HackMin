package com.hackmin.app.data.api;


import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.restaurant.*;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Maps to README Section 3 — 고객 · 음식점 / 메뉴
 * All endpoints here are public (no auth required), per the README's
 * permission column.
 */
public interface RestaurantApi {

    /**
     * 음식점/음식 검색.
     * 백엔드 restaurant_search 파라미터: q(음식명·음식점명), cuisine(종류),
     * min_price, max_price, sort, page.
     */
    @GET("restaurants")
    Call<PagedResponse<RestaurantSummaryDto>> searchRestaurants(
            @Query("q") String keyword,
            @Query("cuisine") String cuisine,
            @Query("min_price") Long minPrice,
            @Query("max_price") Long maxPrice,
            @Query("sort") String sort, // "rating" | "delivery_fee" | "min_order" | "newest"
            @Query("page") Integer page
    );

    @GET("restaurants/{id}")
    Call<RestaurantDetailDto> getRestaurantDetail(@Path("id") long restaurantId);

    @GET("restaurants/{id}/menus")
    Call<PagedResponse<MenuDto>> getRestaurantMenus(@Path("id") long restaurantId);

    @GET("restaurants/{id}/reviews")
    Call<PagedResponse<RestaurantReviewDto>> getRestaurantReviews(
            @Path("id") long restaurantId,
            @Query("page") Integer page
    );

    @GET("menus/{id}")
    Call<MenuDto> getMenuDetail(@Path("id") long menuId);
}
