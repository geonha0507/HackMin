package com.hackmin.app.ui.restaurant;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.restaurant.RestaurantReviewDto;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.util.RatingFormat;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 음식점 리뷰 목록 화면.
 * 공개 API(GET /restaurants/{id}/reviews)로 다른 사용자의 리뷰까지 보여준다.
 */
public class RestaurantReviewsActivity extends com.hackmin.app.ui.common.BaseActivity {

    public static final String EXTRA_RESTAURANT_ID = "restaurant_id";
    public static final String EXTRA_RESTAURANT_NAME = "restaurant_name";

    private long restaurantId = -1;

    private RecyclerView rvReviews;
    private ProgressBar pbLoading;
    private TextView tvEmpty;
    private TextView tvRatingSummary;
    private RestaurantReviewAdapter adapter;

    public static Intent newIntent(Context ctx, long restaurantId, String restaurantName) {
        Intent i = new Intent(ctx, RestaurantReviewsActivity.class);
        i.putExtra(EXTRA_RESTAURANT_ID, restaurantId);
        i.putExtra(EXTRA_RESTAURANT_NAME, restaurantName);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_restaurant_reviews);

        restaurantId = getIntent().getLongExtra(EXTRA_RESTAURANT_ID, -1L);
        if (restaurantId < 0) {
            finish();
            return;
        }

        ImageButton btnBack = findViewById(R.id.btn_back);
        TextView header = findViewById(R.id.tv_reviews_header);
        rvReviews = findViewById(R.id.rv_reviews);
        pbLoading = findViewById(R.id.pb_loading);
        tvEmpty = findViewById(R.id.tv_reviews_empty);
        tvRatingSummary = findViewById(R.id.tv_rating_summary);

        String name = getIntent().getStringExtra(EXTRA_RESTAURANT_NAME);
        if (name != null && !name.isEmpty()) {
            header.setText(name + " 리뷰");
        }

        btnBack.setOnClickListener(v -> finish());

        adapter = new RestaurantReviewAdapter();
        rvReviews.setLayoutManager(new LinearLayoutManager(this));
        rvReviews.setAdapter(adapter);

        loadReviews();
    }

    private void loadReviews() {
        pbLoading.setVisibility(View.VISIBLE);
        ApiClient.restaurantApi(this).getRestaurantReviews(restaurantId, null)
                .enqueue(new Callback<PagedResponse<RestaurantReviewDto>>() {
                    @Override
                    public void onResponse(Call<PagedResponse<RestaurantReviewDto>> call,
                                           Response<PagedResponse<RestaurantReviewDto>> response) {
                        pbLoading.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null) {
                            List<RestaurantReviewDto> results = response.body().getResults();
                            adapter.submit(results);
                            boolean empty = results == null || results.isEmpty();
                            tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                            bindRatingSummary(results);
                        } else {
                            tvEmpty.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onFailure(Call<PagedResponse<RestaurantReviewDto>> call, Throwable t) {
                        pbLoading.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.VISIBLE);
                    }
                });
    }

    /** 로드된 리뷰들의 평균 별점을 계산해 상단에 표시한다. */
    private void bindRatingSummary(List<RestaurantReviewDto> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            tvRatingSummary.setVisibility(View.GONE);
            return;
        }
        double sum = 0;
        for (RestaurantReviewDto r : reviews) {
            sum += r.getRating();
        }
        double avg = sum / reviews.size();
        tvRatingSummary.setText(RatingFormat.stars(avg) + " · 리뷰 " + reviews.size() + "개");
        tvRatingSummary.setVisibility(View.VISIBLE);
    }
}
