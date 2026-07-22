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
import com.hackmin.app.data.model.restaurant.MenuDto;
import com.hackmin.app.data.model.restaurant.RestaurantSummaryDto;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.ui.common.BottomNav;
import com.hackmin.app.ui.notice.NoticeActivity;
import com.hackmin.app.util.ImageLoader;
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

    // 이미지 프리로드는 앱 실행 초기 1회만 수행(검색/카테고리 재로드마다 반복 금지).
    private boolean imagesPrefetched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        etHomeSearch = findViewById(R.id.et_home_search);
        tilHomeSearch = findViewById(R.id.til_home_search);
        rvRestaurants = findViewById(R.id.rv_restaurants);
        pbLoading = findViewById(R.id.pb_loading);
        tvEmpty = findViewById(R.id.tv_empty);

        // 상단 아이콘 버튼(공지사항)
        ImageButton btnNotification = findViewById(R.id.btn_notification);
        if (btnNotification != null) {
            btnNotification.setOnClickListener(v ->
                    startActivity(new Intent(this, NoticeActivity.class)));
        }

        // 하단 네비게이션 바 (공용): 장바구니/마이페이지 이동 + 현재 탭(홈) 강조
        BottomNav.setup(this, BottomNav.Tab.HOME);
        // 홈 탭은 현재 화면 — 탭하면 검색/카테고리 필터 해제하고 전체 목록으로 리셋
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            etHomeSearch.setText("");
            loadRestaurants(null, null);
            rvRestaurants.smoothScrollToPosition(0);
        });

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
                R.id.category_chinese, R.id.category_chicken, R.id.category_pizza,
                R.id.category_cafe, R.id.category_stew, R.id.category_korean,
                R.id.category_bunsik
        };
        // 백엔드 cuisine_type 검색어와 매칭되는 키워드
        String[] categoryQueries = {"중식", "치킨", "피자", "카페", "찜", "한식", "분식"};

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
                            // 앱 실행 초기 1회: 음식점/메뉴 사진을 미리 받아 캐시에 채운다.
                            if (!imagesPrefetched) {
                                imagesPrefetched = true;
                                prefetchImages(results);
                            }
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

    /**
     * 음식점 썸네일과 각 음식점 메뉴 사진을 백그라운드로 미리 받아 Glide 캐시에 채운다.
     * → 메뉴창을 눌렀을 때 사진이 즉시 표시된다(로딩 지연 해소).
     */
    private void prefetchImages(List<RestaurantSummaryDto> restaurants) {
        if (restaurants == null) return;
        for (RestaurantSummaryDto r : restaurants) {
            ImageLoader.preload(this, r.getImage());   // 음식점 썸네일
            prefetchMenuImages(r.getId());             // 해당 음식점의 메뉴 사진들
        }
    }

    private void prefetchMenuImages(long restaurantId) {
        ApiClient.restaurantApi(this).getRestaurantMenus(restaurantId)
                .enqueue(new Callback<PagedResponse<MenuDto>>() {
                    @Override
                    public void onResponse(Call<PagedResponse<MenuDto>> call,
                                           Response<PagedResponse<MenuDto>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getResults() != null) {
                            for (MenuDto m : response.body().getResults()) {
                                ImageLoader.preload(HomeActivity.this, m.getImage());
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<PagedResponse<MenuDto>> call, Throwable t) {
                        // 프리로드 실패는 무시(실제 진입 시 다시 로드됨).
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
