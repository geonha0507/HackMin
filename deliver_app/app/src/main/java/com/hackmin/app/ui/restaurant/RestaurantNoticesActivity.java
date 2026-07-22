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
import com.hackmin.app.data.model.restaurant.RestaurantNoticeDto;
import com.hackmin.app.network.ApiClient;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 매장 공지사항 목록 화면.
 * 공개 API(GET /restaurants/{id}/notices)로 점주가 등록한 공지를 보여준다.
 */
public class RestaurantNoticesActivity extends AppCompatActivity {

    public static final String EXTRA_RESTAURANT_ID = "restaurant_id";
    public static final String EXTRA_RESTAURANT_NAME = "restaurant_name";

    private long restaurantId = -1;

    private RecyclerView rvNotices;
    private ProgressBar pbLoading;
    private TextView tvEmpty;
    private RestaurantNoticeAdapter adapter;

    public static Intent newIntent(Context ctx, long restaurantId, String restaurantName) {
        Intent i = new Intent(ctx, RestaurantNoticesActivity.class);
        i.putExtra(EXTRA_RESTAURANT_ID, restaurantId);
        i.putExtra(EXTRA_RESTAURANT_NAME, restaurantName);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_restaurant_notices);

        restaurantId = getIntent().getLongExtra(EXTRA_RESTAURANT_ID, -1L);
        if (restaurantId < 0) {
            finish();
            return;
        }

        ImageButton btnBack = findViewById(R.id.btn_back);
        TextView header = findViewById(R.id.tv_notices_header);
        rvNotices = findViewById(R.id.rv_notices);
        pbLoading = findViewById(R.id.pb_loading);
        tvEmpty = findViewById(R.id.tv_notices_empty);

        String name = getIntent().getStringExtra(EXTRA_RESTAURANT_NAME);
        if (name != null && !name.isEmpty()) {
            header.setText(name + " 공지사항");
        }

        btnBack.setOnClickListener(v -> finish());

        adapter = new RestaurantNoticeAdapter();
        rvNotices.setLayoutManager(new LinearLayoutManager(this));
        rvNotices.setAdapter(adapter);

        loadNotices();
    }

    private void loadNotices() {
        pbLoading.setVisibility(View.VISIBLE);
        ApiClient.restaurantApi(this).getRestaurantNotices(restaurantId, null)
                .enqueue(new Callback<PagedResponse<RestaurantNoticeDto>>() {
                    @Override
                    public void onResponse(Call<PagedResponse<RestaurantNoticeDto>> call,
                                           Response<PagedResponse<RestaurantNoticeDto>> response) {
                        pbLoading.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null) {
                            List<RestaurantNoticeDto> results = response.body().getResults();
                            adapter.submit(results);
                            boolean empty = results == null || results.isEmpty();
                            tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                        } else {
                            tvEmpty.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onFailure(Call<PagedResponse<RestaurantNoticeDto>> call, Throwable t) {
                        pbLoading.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.VISIBLE);
                    }
                });
    }
}
