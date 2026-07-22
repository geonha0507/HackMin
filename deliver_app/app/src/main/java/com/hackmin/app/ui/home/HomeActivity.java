package com.hackmin.app.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.hackmin.app.R;
import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.restaurant.RestaurantSummaryDto;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.ui.cart.CartActivity;
import com.hackmin.app.ui.mypage.MyPageActivity;
import com.hackmin.app.ui.notice.NoticeActivity;
import com.hackmin.app.ui.restaurant.RestaurantDetailActivity;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity {

    private TextInputEditText etHomeSearch;
    private TextInputLayout tilHomeSearch;
    private RecyclerView rvRestaurants;
    private ProgressBar pbLoading;
    private TextView tvEmpty;
    private RestaurantAdapter adapter;

    // 뒤로가기 두 번 눌러 종료: 마지막 뒤로가기 시각(ms)과 허용 간격.
    private static final long BACK_EXIT_INTERVAL_MS = 2000L;
    private long lastBackPressedTime = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        etHomeSearch = findViewById(R.id.et_home_search);
        tilHomeSearch = findViewById(R.id.til_home_search);
        rvRestaurants = findViewById(R.id.rv_restaurants);
        pbLoading = findViewById(R.id.pb_loading);
        tvEmpty = findViewById(R.id.tv_empty);

        // 상단 아이콘 버튼
        ImageButton btnNotification = findViewById(R.id.btn_notification);
        ImageButton btnCart = findViewById(R.id.btn_cart);
        ImageButton btnMypage = findViewById(R.id.btn_mypage);
        if (btnNotification != null) {
            btnNotification.setOnClickListener(v ->
                    startActivity(new Intent(this, NoticeActivity.class)));
        }
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

        // 검색창(키보드 검색 버튼 + 돋보기 아이콘 둘 다 같은 동작 수행)
        etHomeSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                triggerSearch();
                return true;
            }
            return false;
        });
        tilHomeSearch.setStartIconOnClickListener(v -> triggerSearch());

        setupCategoryListeners();

        // 추천메뉴: 검색/카테고리 해제하고 추천 목록(평점순)으로 초기화
        View recommend = findViewById(R.id.category_recommend);
        if (recommend != null) {
            recommend.setOnClickListener(v -> {
                etHomeSearch.setText("");
                loadRestaurants(null, null);
            });
        }

        // 뒤로가기 두 번 연속(2초 이내)이면 앱 종료, 한 번이면 안내 토스트.
        setupDoubleBackToExit();

        // 최초 진입: 전체 목록(평점순)
        loadRestaurants(null, null);
    }

    /** 메인 화면에서 뒤로가기를 두 번 눌러야 앱이 종료되도록 처리한다. */
    private void setupDoubleBackToExit() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                long now = System.currentTimeMillis();
                if (now - lastBackPressedTime < BACK_EXIT_INTERVAL_MS) {
                    // 짧은 간격 내 두 번째 뒤로가기 → 앱 종료(태스크 전체 종료).
                    finishAffinity();
                } else {
                    lastBackPressedTime = now;
                    Toast.makeText(HomeActivity.this,
                            "한 번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void triggerSearch() {
        String keyword = etHomeSearch.getText() != null
                ? etHomeSearch.getText().toString().trim() : "";
        loadRestaurants(keyword.isEmpty() ? null : keyword, null);
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
