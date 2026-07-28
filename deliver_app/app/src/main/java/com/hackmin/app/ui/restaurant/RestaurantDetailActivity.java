package com.hackmin.app.ui.restaurant;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.model.cart.AddCartItemRequest;
import com.hackmin.app.data.model.cart.CartSummaryDto;
import com.hackmin.app.data.model.cart.CartDto;
import com.hackmin.app.data.model.cart.CartItemDto;
import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.restaurant.MenuDto;
import com.hackmin.app.data.model.restaurant.MenuOptionDto;
import com.hackmin.app.data.model.restaurant.MenuOptionGroupDto;
import com.hackmin.app.data.model.restaurant.RestaurantDetailDto;
import com.hackmin.app.data.model.restaurant.RestaurantNoticeDto;
import com.hackmin.app.data.model.restaurant.RestaurantReviewDto;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.ui.cart.CartActivity;
import com.hackmin.app.util.CartRules;
import com.hackmin.app.util.ImageLoader;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RestaurantDetailActivity extends AppCompatActivity {

    public static final String EXTRA_RESTAURANT_ID = "restaurant_id";
    public static final String EXTRA_RESTAURANT_NAME = "restaurant_name";

    private long restaurantId;
    private boolean isOpen = true;
    private String restaurantName;

    private TextView tvName, tvCuisine, tvMeta, tvAddress, tvNoticesBannerLabel;
    private View noticesBanner;
    private ImageView ivImage;
    private RecyclerView rvMenus;
    private ProgressBar pbLoading;
    private MenuAdapter adapter;
    private View cartBar;
    private android.widget.Button btnCartBar;

    private final NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);

    private RestaurantDetailDto detail;
    private Double reviewAvg;   // 리뷰 평균(있으면 헤더 별점에 우선 반영)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_restaurant_detail);
        com.hackmin.app.ui.common.EdgeToEdgeUtil.apply(this);

        restaurantId = getIntent().getLongExtra(EXTRA_RESTAURANT_ID, -1L);
        if (restaurantId < 0) {
            Toast.makeText(this, "잘못된 접근입니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvName = findViewById(R.id.tv_detail_name);
        tvCuisine = findViewById(R.id.tv_detail_cuisine);
        tvMeta = findViewById(R.id.tv_detail_meta);
        tvAddress = findViewById(R.id.tv_detail_address);
        ivImage = findViewById(R.id.iv_detail_image);
        rvMenus = findViewById(R.id.rv_menus);
        pbLoading = findViewById(R.id.pb_detail_loading);

        // 진입 시점에 알고 있는 이름은 먼저 표시(체감 속도).
        String presetName = getIntent().getStringExtra(EXTRA_RESTAURANT_NAME);
        if (presetName != null) {
            tvName.setText(presetName);
            restaurantName = presetName;
        }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_detail_cart).setOnClickListener(v ->
                startActivity(new Intent(this, CartActivity.class)));
        findViewById(R.id.btn_view_reviews).setOnClickListener(v ->
                startActivity(RestaurantReviewsActivity.newIntent(this, restaurantId, restaurantName)));

        noticesBanner = findViewById(R.id.btn_view_notices);
        tvNoticesBannerLabel = findViewById(R.id.tv_notices_banner_label);
        noticesBanner.setOnClickListener(v ->
                startActivity(RestaurantNoticesActivity.newIntent(this, restaurantId, restaurantName)));
        noticesBanner.setVisibility(View.GONE); // 공지가 있을 때만 표시(loadNotices에서 갱신)

        adapter = new MenuAdapter(this::onMenuClicked);
        adapter.setRestaurantName(restaurantName);  // 유의사항 문구용(이후 상세 로드 시 갱신)
        rvMenus.setLayoutManager(new LinearLayoutManager(this));
        rvMenus.setAdapter(adapter);

        // 하단 장바구니 담기 바: 누르면 장바구니로 이동.
        cartBar = findViewById(R.id.cart_bar);
        btnCartBar = findViewById(R.id.btn_cart_bar);
        btnCartBar.setOnClickListener(v -> startActivity(new Intent(this, CartActivity.class)));

        loadDetail();
        loadMenus();
        loadReviewAverage();
        loadNotices();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 장바구니에서 수량이 바뀌었을 수 있으니 다시 진입 시 금액 갱신.
        refreshCartBar();
    }

    /** 하단 장바구니 바의 금액을 갱신한다. 담긴 게 있으면 "N원 담기"로 표시, 없으면 숨김. */
    private void refreshCartBar() {
        ApiClient.cartApi(this).getCartSummary().enqueue(new Callback<CartSummaryDto>() {
            @Override
            public void onResponse(Call<CartSummaryDto> call, Response<CartSummaryDto> response) {
                int total = 0;
                if (response.isSuccessful() && response.body() != null) {
                    total = response.body().getTotal();
                }
                if (total > 0) {
                    btnCartBar.setText(won.format(total) + "원 담기");
                    cartBar.setVisibility(View.VISIBLE);
                } else {
                    cartBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(Call<CartSummaryDto> call, Throwable t) {
                // 실패 시 조용히 무시(바 상태 유지).
            }
        });
    }

    /** 매장 공지사항 유무를 확인해 배너에 최신 공지 제목을 보여준다. 공지가 없으면 배너를 숨긴다. */
    private void loadNotices() {
        ApiClient.restaurantApi(this).getRestaurantNotices(restaurantId, null)
                .enqueue(new Callback<PagedResponse<RestaurantNoticeDto>>() {
                    @Override
                    public void onResponse(Call<PagedResponse<RestaurantNoticeDto>> call,
                                           Response<PagedResponse<RestaurantNoticeDto>> response) {
                        if (!response.isSuccessful() || response.body() == null) return;
                        List<RestaurantNoticeDto> notices = response.body().getResults();
                        if (notices == null || notices.isEmpty()) return;
                        tvNoticesBannerLabel.setText(notices.get(0).getTitle());
                        noticesBanner.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onFailure(Call<PagedResponse<RestaurantNoticeDto>> call, Throwable t) {
                        // 공지 배너는 부가 기능이므로 실패해도 화면에 영향 없음(숨김 유지).
                    }
                });
    }

    // ── 음식점 상세 헤더 ──────────────────────────────────

    private void loadDetail() {
        ApiClient.restaurantApi(this).getRestaurantDetail(restaurantId)
                .enqueue(new Callback<RestaurantDetailDto>() {
                    @Override
                    public void onResponse(Call<RestaurantDetailDto> call,
                                           Response<RestaurantDetailDto> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            bindHeader(response.body());
                        }
                    }

                    @Override
                    public void onFailure(Call<RestaurantDetailDto> call, Throwable t) {
                        // 헤더 실패는 치명적이지 않음(메뉴 로드는 별도).
                    }
                });
    }

    private void bindHeader(RestaurantDetailDto r) {
        detail = r;
        restaurantName = r.getName();
        adapter.setRestaurantName(restaurantName);  // 유의사항 문구를 실제 매장명으로 갱신
        String cuisine = r.getCuisineType();
        tvCuisine.setText(cuisine == null || cuisine.isEmpty() ? "음식점" : cuisine);
        String addr = r.getAddress();
        tvAddress.setText(addr == null || addr.isEmpty() ? "" : addr);
        ImageLoader.load(ivImage, r.getImage());
        isOpen = r.isOpen();
        tvName.setText(isOpen ? r.getName() : (r.getName() + " (영업종료)"));
        updateMeta();
    }

    /** 별점은 리뷰 평균(reviewAvg)이 있으면 그것을, 없으면 서버 값을 사용한다. */
    private void updateMeta() {
        if (detail == null) return;
        double rating = reviewAvg != null ? reviewAvg : detail.getRating();
        tvMeta.setText("⭐ " + String.format(Locale.KOREA, "%.1f", rating)
                + " · 배달비 " + won.format(detail.getDeliveryFee()) + "원"
                + " · 최소주문 " + won.format(detail.getMinOrderAmount()) + "원");
    }

    /** 리뷰 평균을 계산해 헤더 별점에 반영한다(서버 rating이 리뷰와 분리돼 있어 보정). */
    private void loadReviewAverage() {
        ApiClient.restaurantApi(this).getRestaurantReviews(restaurantId, null)
                .enqueue(new Callback<PagedResponse<RestaurantReviewDto>>() {
                    @Override
                    public void onResponse(Call<PagedResponse<RestaurantReviewDto>> call,
                                           Response<PagedResponse<RestaurantReviewDto>> response) {
                        if (!response.isSuccessful() || response.body() == null) return;
                        List<RestaurantReviewDto> reviews = response.body().getResults();
                        if (reviews == null || reviews.isEmpty()) return;
                        double sum = 0;
                        for (RestaurantReviewDto r : reviews) sum += r.getRating();
                        reviewAvg = sum / reviews.size();
                        updateMeta();
                    }

                    @Override
                    public void onFailure(Call<PagedResponse<RestaurantReviewDto>> call, Throwable t) {
                        // 평균 보정 실패는 무시(서버 rating 그대로 표시).
                    }
                });
    }

    // ── 메뉴 목록 ────────────────────────────────────────

    private void loadMenus() {
        pbLoading.setVisibility(View.VISIBLE);
        ApiClient.restaurantApi(this).getRestaurantMenus(restaurantId)
                .enqueue(new Callback<PagedResponse<MenuDto>>() {
                    @Override
                    public void onResponse(Call<PagedResponse<MenuDto>> call,
                                           Response<PagedResponse<MenuDto>> response) {
                        pbLoading.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null) {
                            adapter.submit(response.body().getResults());
                        } else {
                            Toast.makeText(RestaurantDetailActivity.this,
                                    "메뉴를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<PagedResponse<MenuDto>> call, Throwable t) {
                        pbLoading.setVisibility(View.GONE);
                        Toast.makeText(RestaurantDetailActivity.this,
                                "네트워크 연결 실패 (백엔드 서버 확인 필요)", Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ── 메뉴 선택 → 옵션 상세 조회 ─────────────────────────

    private void onMenuClicked(MenuDto menuFromList) {
        if (!isOpen) {
            Toast.makeText(this, "영업 종료된 매장입니다. 영업 시간에 다시 주문해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        // 목록 응답에는 옵션이 없으므로 상세를 다시 조회한다.
        ApiClient.restaurantApi(this).getMenuDetail(menuFromList.getId())
                .enqueue(new Callback<MenuDto>() {
                    @Override
                    public void onResponse(Call<MenuDto> call, Response<MenuDto> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            showOptionDialog(response.body());
                        } else {
                            // 옵션 조회 실패 시 옵션 없이 1개 담기 시도.
                            addToCart(menuFromList.getId(), new ArrayList<>(), 1);
                        }
                    }

                    @Override
                    public void onFailure(Call<MenuDto> call, Throwable t) {
                        Toast.makeText(RestaurantDetailActivity.this,
                                "네트워크 연결 실패", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** 수량 스테퍼 + 옵션 체크박스를 표시하고, 선택값을 모아 장바구니에 담는다. */
    private void showOptionDialog(MenuDto menu) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        container.setPadding(pad, dp(8), pad, dp(8));

        // ── 수량 스테퍼 (테두리 박스: − [n개] +) ──
        final int[] qty = {1};

        // 둥근 테두리 배경.
        android.graphics.drawable.GradientDrawable stepperBg =
                new android.graphics.drawable.GradientDrawable();
        stepperBg.setColor(android.graphics.Color.WHITE);
        stepperBg.setStroke(dp(1), android.graphics.Color.parseColor("#DDDDDD"));
        stepperBg.setCornerRadius(dp(12));

        LinearLayout stepper = new LinearLayout(this);
        stepper.setOrientation(LinearLayout.HORIZONTAL);
        stepper.setBackground(stepperBg);
        stepper.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams stepperLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        stepperLp.bottomMargin = dp(8);
        stepper.setLayoutParams(stepperLp);

        TextView btnMinus = new TextView(this);
        btnMinus.setText("−");
        btnMinus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        btnMinus.setGravity(android.view.Gravity.CENTER);
        btnMinus.setLayoutParams(new LinearLayout.LayoutParams(dp(56), LinearLayout.LayoutParams.MATCH_PARENT));
        btnMinus.setClickable(true);
        btnMinus.setFocusable(true);

        final TextView tvQty = new TextView(this);
        tvQty.setText("1개");
        tvQty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvQty.setGravity(android.view.Gravity.CENTER);
        tvQty.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        TextView btnPlus = new TextView(this);
        btnPlus.setText("+");
        btnPlus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        btnPlus.setGravity(android.view.Gravity.CENTER);
        btnPlus.setLayoutParams(new LinearLayout.LayoutParams(dp(56), LinearLayout.LayoutParams.MATCH_PARENT));
        btnPlus.setClickable(true);
        btnPlus.setFocusable(true);

        btnMinus.setOnClickListener(v -> {
            if (qty[0] > 1) { qty[0]--; tvQty.setText(qty[0] + "개"); }
        });
        btnPlus.setOnClickListener(v -> {
            if (qty[0] >= CartRules.MAX_ITEM_QUANTITY) {
                Toast.makeText(this, CartRules.MAX_QUANTITY_MESSAGE, Toast.LENGTH_SHORT).show();
                return;
            }
            qty[0]++; tvQty.setText(qty[0] + "개");
        });

        stepper.addView(btnMinus);
        stepper.addView(tvQty);
        stepper.addView(btnPlus);
        container.addView(stepper);

        // ── 옵션 그룹(있으면) ──
        List<MenuOptionGroupDto> groups = menu.getOptionGroups();
        List<CheckBox> boxes = new ArrayList<>();
        List<Integer> boxOptionIds = new ArrayList<>();

        if (groups != null) {
            for (MenuOptionGroupDto group : groups) {
                TextView header = new TextView(this);
                String title = group.getName();
                if (group.isRequired()) {
                    title += " (필수)";
                }
                header.setText(title);
                header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                header.setPadding(0, dp(12), 0, dp(4));
                container.addView(header);

                if (group.getOptions() != null) {
                    for (MenuOptionDto opt : group.getOptions()) {
                        CheckBox cb = new CheckBox(this);
                        String label = opt.getName();
                        if (opt.getExtraPrice() > 0) {
                            label += " (+" + won.format(opt.getExtraPrice()) + "원)";
                        }
                        cb.setText(label);
                        container.addView(cb);
                        boxes.add(cb);
                        boxOptionIds.add((int) opt.getId());
                    }
                }
            }
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(container);

        new AlertDialog.Builder(this)
                .setTitle(menu.getName() + "  " + won.format(menu.getPrice()) + "원")
                .setView(scroll)
                .setPositiveButton("장바구니 담기", (dialog, which) -> {
                    List<Integer> selected = new ArrayList<>();
                    for (int i = 0; i < boxes.size(); i++) {
                        if (boxes.get(i).isChecked()) {
                            selected.add(boxOptionIds.get(i));
                        }
                    }
                    addToCart(menu.getId(), selected, qty[0]);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // ── 장바구니 담기 (A→B 핸드오프) ───────────────────────

    /**
     * 담기 전 현재 장바구니를 조회해 "동일 메뉴+옵션"의 기존 수량 + 신규 수량이
     * 50개를 넘으면 담지 않고 안내한다. (서버가 같은 메뉴+옵션을 합산하므로 선체크)
     */
    private void addToCart(long menuId, List<Integer> optionIds, int quantity) {
        ApiClient.cartApi(this).getCart().enqueue(new Callback<CartDto>() {
            @Override
            public void onResponse(Call<CartDto> call, Response<CartDto> response) {
                int existing = (response.isSuccessful() && response.body() != null)
                        ? existingQuantity(response.body(), menuId, optionIds) : 0;
                if (existing + quantity > CartRules.MAX_ITEM_QUANTITY) {
                    Toast.makeText(RestaurantDetailActivity.this,
                            CartRules.MAX_QUANTITY_MESSAGE, Toast.LENGTH_SHORT).show();
                    return;
                }
                postAddToCart(menuId, optionIds, quantity);
            }

            @Override
            public void onFailure(Call<CartDto> call, Throwable t) {
                // 카트 조회 실패 시엔 담기를 시도한다(스테퍼 상한으로 단건은 이미 50 이하).
                postAddToCart(menuId, optionIds, quantity);
            }
        });
    }

    /** 현재 장바구니에서 동일 메뉴 + 동일 옵션 조합의 수량 합계를 구한다(서버 합산 규칙과 동일). */
    private int existingQuantity(CartDto cart, long menuId, List<Integer> optionIds) {
        if (cart.getItems() == null) return 0;
        List<Integer> want = new ArrayList<>(optionIds);
        Collections.sort(want);
        int sum = 0;
        for (CartItemDto item : cart.getItems()) {
            if (item.getMenu() != menuId) continue;
            List<Integer> have = item.getOptions() == null
                    ? new ArrayList<>() : new ArrayList<>(item.getOptions());
            Collections.sort(have);
            if (have.equals(want)) sum += item.getQuantity();
        }
        return sum;
    }

    /** 실제 담기 요청(POST /cart/items). */
    private void postAddToCart(long menuId, List<Integer> optionIds, int quantity) {
        AddCartItemRequest request = new AddCartItemRequest(menuId, quantity, optionIds);
        ApiClient.cartApi(this).addItem(request).enqueue(new Callback<CartDto>() {
            @Override
            public void onResponse(Call<CartDto> call, Response<CartDto> response) {
                if (response.isSuccessful()) {
                    refreshCartBar();  // 담긴 금액을 하단 바에 반영(별도 안내 다이얼로그 없음).
                    Toast.makeText(RestaurantDetailActivity.this,
                            "장바구니에 담았습니다.", Toast.LENGTH_SHORT).show();
                } else if (response.code() == 409) {
                    // 다른 음식점 메뉴가 담겨 있음 → 비우고 교체할지 확인.
                    promptReplaceCart(menuId, optionIds, quantity);
                } else if (response.code() == 401) {
                    Toast.makeText(RestaurantDetailActivity.this,
                            "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(RestaurantDetailActivity.this,
                            "장바구니 담기에 실패했습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<CartDto> call, Throwable t) {
                Toast.makeText(RestaurantDetailActivity.this,
                        "네트워크 연결 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** 다른 음식점 메뉴 충돌(409) 시 교체 여부 확인. */
    private void promptReplaceCart(long menuId, List<Integer> optionIds, int quantity) {
        new AlertDialog.Builder(this)
                .setTitle("장바구니 교체")
                .setMessage("장바구니에 다른 음식점의 메뉴가 담겨 있습니다.\n기존 장바구니를 비우고 새로 담을까요?")
                .setPositiveButton("비우고 담기", (d, w) -> clearCartThenAdd(menuId, optionIds, quantity))
                .setNegativeButton("취소", null)
                .show();
    }

    /** 장바구니를 비운 뒤(DELETE /cart) 같은 메뉴를 다시 담는다. */
    private void clearCartThenAdd(long menuId, List<Integer> optionIds, int quantity) {
        ApiClient.cartApi(this).clearCart().enqueue(new Callback<CartDto>() {
            @Override
            public void onResponse(Call<CartDto> call, Response<CartDto> response) {
                if (response.isSuccessful()) {
                    // 비운 직후라 기존 수량 0 → 선체크 없이 바로 담는다.
                    postAddToCart(menuId, optionIds, quantity);
                } else {
                    Toast.makeText(RestaurantDetailActivity.this,
                            "장바구니 비우기에 실패했습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<CartDto> call, Throwable t) {
                Toast.makeText(RestaurantDetailActivity.this,
                        "네트워크 연결 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
