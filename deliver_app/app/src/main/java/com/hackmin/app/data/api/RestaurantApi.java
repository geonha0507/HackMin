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
     * 🎯 vulnerable/secure dual-mode (SQL Injection in the search query
     * path in vulnerable mode). Supports name/restaurant-name/category/
     * price-range search plus rating/distance/order-count sort per the
     * spec doc — confirm exact query param names with the backend dev
     * (guessed below as name/category/min_price/max_price/sort).
     */
    @GET("restaurants")
    Call<PagedResponse<RestaurantSummaryDto>> searchRestaurants(
            @Query("q") String keyword,
            @Query("category") String category,
            @Query("min_price") Long minPrice,
            @Query("max_price") Long maxPrice,
            @Query("sort") String sort, // "rating" | "distance" | "order_count"
            @Query("lat") Double latitude,
            @Query("lng") Double longitude,
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
