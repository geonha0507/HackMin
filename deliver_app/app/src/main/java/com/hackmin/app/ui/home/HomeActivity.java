package com.hackmin.app.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.hackmin.app.R;
import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.restaurant.RestaurantSummaryDto;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.ui.cart.CartActivity;
import com.hackmin.app.ui.mypage.MyPageActivity;
import com.hackmin.app.ui.restaurant.RestaurantDetailActivity;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity {

    private TextInputEditText etHomeSearch;
    private RecyclerView rvRestaurants;
    private ProgressBar pbLoading;
    private TextView tvEmpty;
    private RestaurantAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        etHomeSearch = findViewById(R.id.et_home_search);
        rvRestaurants = findViewById(R.id.rv_restaurants);
        pbLoading = findViewById(R.id.pb_loading);
        tvEmpty = findViewById(R.id.tv_empty);

        // 상단 아이콘 버튼
        ImageButton btnCart = findViewById(R.id.btn_cart);
        ImageButton btnMypage = findViewById(R.id.btn_mypage);
        if (btnCart != null) {
            btnCart.setOnClickListener(v ->
                    startActivity(new Intent(this, CartActivity.class)));
        }
        if (btnMypage != null) {
            btnMypage.setOnClickListener(v ->
                    startActivity(new Intent(this, MyPageActivity.class)));
        }

        // 음식점 목록
        adapter = new RestaurantAdapter(restaurant -> {
            Intent intent = new Intent(this, RestaurantDetailActivity.class);
            intent.putExtra(RestaurantDetailActivity.EXTRA_RESTAURANT_ID, restaurant.getId());
            intent.putExtra(RestaurantDetailActivity.EXTRA_RESTAURANT_NAME, restaurant.getName());
            startActivity(intent);
        });
        rvRestaurants.setLayoutManager(new LinearLayoutManager(this));
        rvRestaurants.setAdapter(adapter);

        // 검색창(키보드 검색 버튼)
        etHomeSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String keyword = etHomeSearch.getText() != null
                        ? etHomeSearch.getText().toString().trim() : "";
                loadRestaurants(keyword.isEmpty() ? null : keyword, null);
                return true;
            }
            return false;
        });

        setupCategoryListeners();

        // 최초 진입: 전체 목록(평점순)
        loadRestaurants(null, null);
    }

    private void setupCategoryListeners() {
        int[] categoryIds = {
                R.id.category_chinese, R.id.category_chicken,
                R.id.category_cafe, R.id.category_stew, R.id.category_korean
        };
        // 백엔드 cuisine_type 검색어와 매칭되는 키워드
        String[] categoryQueries = {"중식", "치킨", "카페", "찜", "한식"};

        for (int i = 0; i < categoryIds.length; i++) {
            final String query = categoryQueries[i];
            findViewById(categoryIds[i]).setOnClickListener(v -> {
                etHomeSearch.setText("");
                loadRestaurants(query, null);
            });
        }
    }

    /**
     * 음식점 검색/목록 로드.
     *
     * @param keyword q 파라미터(음식명·음식점명). null이면 전체.
     * @param sort    정렬(rating|delivery_fee|min_order|newest). null이면 기본(평점순).
     */
    private void loadRestaurants(String keyword, String sort) {
        showLoading();
        ApiClient.restaurantApi(this)
                .searchRestaurants(keyword, null, null, null, sort, null)
                .enqueue(new Callback<PagedResponse<RestaurantSummaryDto>>() {
                    @Override
                    public void onResponse(Call<PagedResponse<RestaurantSummaryDto>> call,
                                           Response<PagedResponse<RestaurantSummaryDto>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<RestaurantSummaryDto> results = response.body().getResults();
                            adapter.submit(results);
                            showResult(results == null || results.isEmpty());
                        } else {
                            showResult(true);
                            Toast.makeText(HomeActivity.this,
                                    "목록을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<PagedResponse<RestaurantSummaryDto>> call, Throwable t) {
                        showResult(true);
                        Toast.makeText(HomeActivity.this,
                                "네트워크 연결 실패 (백엔드 서버 확인 필요)", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showLoading() {
        pbLoading.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        rvRestaurants.setVisibility(View.GONE);
    }

    private void showResult(boolean empty) {
        pbLoading.setVisibility(View.GONE);
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvRestaurants.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
