package com.hackmin.app.data.api;

import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.restaurant.RestaurantSummaryDto;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.*;

/**
 * 위치 기반 API (/api/v1/locations)
 */
public interface LocationApi {

    /** 주소/키워드로 위치 검색 */
    @GET("locations/search")
    Call<List<Map<String, Object>>> searchLocation(@Query("q") String query);

    /** 내 위치 기반 근처 음식점 조회 */
    @GET("locations/nearby")
    Call<PagedResponse<RestaurantSummaryDto>> getNearbyRestaurants(
            @Query("lat") double latitude,
            @Query("lng") double longitude,
            @Query("radius") Integer radiusMeters,
            @Query("page") Integer page
    );
}
