package com.hackmin.app.ui.cart;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.api.CartApi;
import com.hackmin.app.data.model.cart.ApplyCouponRequest;
import com.hackmin.app.data.model.cart.CartDto;
import com.hackmin.app.data.model.cart.CartSummaryDto;
import com.hackmin.app.data.model.cart.UpdateCartItemRequest;
import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.promotion.CouponDto;
import com.hackmin.app.data.model.promotion.UserCouponDto;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.ui.common.BottomNav;
import com.hackmin.app.ui.order.OrderActivity;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CartActivity extends AppCompatActivity {

    private RecyclerView rvCartItems;
    private TextView tvDeliveryFee, tvTotalPrice, tvDiscount, tvAppliedCoupon;
    private Button btnOrder, btnApplyCoupon;

    private CartItemAdapter adapter;
    private CartApi cartApi;
    private final NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);
    /** 최근 요약(금액·최소주문금액). 주문하기 시 최소금액 검증에 사용. */
    private CartSummaryDto lastSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart_main);

        initApi();
        initViews();
        setupRecyclerView();

        btnOrder.setOnClickListener(v -> {
            if (adapter.getItemCount() == 0) {
                Toast.makeText(this, "장바구니가 비어 있습니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            // 최소 주문금액 검증(배달비 제외 subtotal 기준). 미달이면 주문서로 넘어가지 않는다.
            if (lastSummary != null && lastSummary.getSubtotal() < lastSummary.getMinOrderAmount()) {
                int shortfall = lastSummary.getMinOrderAmount() - lastSummary.getSubtotal();
                new AlertDialog.Builder(this)
                        .setTitle("최소 주문금액 미달")
                        .setMessage("최소 주문금액은 " + won.format(lastSummary.getMinOrderAmount()) + "원입니다.\n"
                                + "현재 " + won.format(lastSummary.getSubtotal()) + "원(배달비 제외) · "
                                + won.format(shortfall) + "원 더 담아주세요.")
                        .setPositiveButton("확인", null)
                        .show();
                return;
            }
            startActivity(new Intent(this, OrderActivity.class));
        });

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        BottomNav.setup(this, BottomNav.Tab.CART);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 다른 화면에서 담고 돌아오는 경우를 위해 진입 시마다 서버 상태로 갱신.
        loadCart();
    }

    private void initApi() {
        // 토큰/모드는 SessionManager가 자동 주입 — Context만 넘기면 된다.
        cartApi = ApiClient.cartApi(this);
    }

    private void initViews() {
        rvCartItems = findViewById(R.id.rvCartItems);
        tvDeliveryFee = findViewById(R.id.tvDeliveryFee);
        tvTotalPrice = findViewById(R.id.tvTotalPrice);
        tvDiscount = findViewById(R.id.tvDiscount);
        tvAppliedCoupon = findViewById(R.id.tvAppliedCoupon);
        btnOrder = findViewById(R.id.btnOrder);
        btnApplyCoupon = findViewById(R.id.btnApplyCoupon);
        btnApplyCoupon.setOnClickListener(v -> showCouponPicker());
    }

    private void setupRecyclerView() {
        adapter = new CartItemAdapter(new CartItemAdapter.OnCartItemActionListener() {
            @Override
            public void onQuantityChanged(long itemId, int newQuantity) {
                changeQuantity(itemId, newQuantity);
            }

            @Override
            public void onDeleteItem(long itemId) {
                deleteItem(itemId);
            }
        });
        rvCartItems.setLayoutManager(new LinearLayoutManager(this));
        rvCartItems.setAdapter(adapter);
    }

    /** GET /cart 로 항목을 채우고 이어서 요약(금액)을 갱신한다. */
    private void loadCart() {
        cartApi.getCart().enqueue(new Callback<CartDto>() {
            @Override
            public void onResponse(@NonNull Call<CartDto> call, @NonNull Response<CartDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    adapter.setItems(response.body().getItems());
                    loadSummary();
                } else {
                    Toast.makeText(CartActivity.this, "장바구니를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<CartDto> call, @NonNull Throwable t) {
                Toast.makeText(CartActivity.this, "네트워크 연결 실패 (서버 확인 필요)", Toast.LENGTH_LONG).show();
            }
        });
    }

    /** GET /cart/summary 로 배달비·총액을 채운다. (CartDto에는 금액 합계가 없음) */
    private void loadSummary() {
        cartApi.getCartSummary().enqueue(new Callback<CartSummaryDto>() {
            @Override
            public void onResponse(@NonNull Call<CartSummaryDto> call, @NonNull Response<CartSummaryDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    CartSummaryDto s = response.body();
                    lastSummary = s;
                    tvDeliveryFee.setText(won.format(s.getDeliveryFee()) + "원");
                    tvTotalPrice.setText(won.format(s.getTotal()) + "원");
                    tvDiscount.setText("-" + won.format(s.getDiscount()) + "원");
                    // 할인 금액으로 쿠폰 적용 여부 표시.
                    tvAppliedCoupon.setText(s.getDiscount() > 0 ? "쿠폰 할인 적용됨" : "적용된 쿠폰 없음");
                }
            }

            @Override
            public void onFailure(@NonNull Call<CartSummaryDto> call, @NonNull Throwable t) {
                // 목록은 이미 표시됨 — 요약만 실패한 경우 조용히 무시.
            }
        });
    }

    /** 보유 쿠폰(GET /me/coupons)을 불러와 선택/해제 다이얼로그를 띄운다. */
    private void showCouponPicker() {
        ApiClient.promotionApi(this).getMyCoupons(null)
                .enqueue(new Callback<PagedResponse<UserCouponDto>>() {
                    @Override
                    public void onResponse(@NonNull Call<PagedResponse<UserCouponDto>> call,
                                           @NonNull Response<PagedResponse<UserCouponDto>> response) {
                        List<UserCouponDto> coupons = response.isSuccessful() && response.body() != null
                                ? response.body().getResults() : null;
                        buildCouponDialog(coupons);
                    }

                    @Override
                    public void onFailure(@NonNull Call<PagedResponse<UserCouponDto>> call, @NonNull Throwable t) {
                        Toast.makeText(CartActivity.this, "쿠폰을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void buildCouponDialog(List<UserCouponDto> coupons) {
        List<String> labels = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        if (coupons != null) {
            for (UserCouponDto uc : coupons) {
                if (uc.isUsed()) continue; // 사용 완료 쿠폰 제외
                CouponDto c = uc.getCoupon();
                if (c == null || c.getCode() == null) continue;
                String discount = "percent".equals(c.getDiscountType())
                        ? c.getDiscountValue() + "%"
                        : won.format(c.getDiscountValue()) + "원";
                labels.add(c.getName() + " (" + discount + " 할인)");
                codes.add(c.getCode());
            }
        }
        labels.add("쿠폰 적용 안 함(해제)");

        String[] items = labels.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("쿠폰 선택")
                .setItems(items, (dialog, which) -> {
                    if (which == codes.size()) {
                        removeCoupon();
                    } else {
                        applyCoupon(codes.get(which));
                    }
                })
                .show();
    }

    private void applyCoupon(String code) {
        cartApi.applyCoupon(new ApplyCouponRequest(code)).enqueue(new Callback<CartDto>() {
            @Override
            public void onResponse(@NonNull Call<CartDto> call, @NonNull Response<CartDto> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(CartActivity.this, "쿠폰이 적용되었습니다.", Toast.LENGTH_SHORT).show();
                    loadSummary();
                } else if (response.code() == 400) {
                    Toast.makeText(CartActivity.this, "최소 주문금액을 확인해주세요.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(CartActivity.this, "쿠폰을 적용할 수 없습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<CartDto> call, @NonNull Throwable t) {
                Toast.makeText(CartActivity.this, "네트워크 연결 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void removeCoupon() {
        cartApi.removeCoupon().enqueue(new Callback<CartDto>() {
            @Override
            public void onResponse(@NonNull Call<CartDto> call, @NonNull Response<CartDto> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(CartActivity.this, "쿠폰이 해제되었습니다.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(CartActivity.this, "쿠폰 해제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                }
                loadSummary();
            }

            @Override
            public void onFailure(@NonNull Call<CartDto> call, @NonNull Throwable t) {
                Toast.makeText(CartActivity.this, "네트워크 연결 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** PUT /cart/items/{id} 수량 변경 후 서버 상태로 재동기화. */
    private void changeQuantity(long itemId, int newQuantity) {
        cartApi.updateItem(itemId, new UpdateCartItemRequest(newQuantity))
                .enqueue(new Callback<CartDto>() {
                    @Override
                    public void onResponse(@NonNull Call<CartDto> call, @NonNull Response<CartDto> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            adapter.setItems(response.body().getItems());
                            loadSummary();
                        } else {
                            Toast.makeText(CartActivity.this, "수량 변경에 실패했습니다.", Toast.LENGTH_SHORT).show();
                            loadCart(); // 실패 시 서버 기준으로 되돌림
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<CartDto> call, @NonNull Throwable t) {
                        Toast.makeText(CartActivity.this, "네트워크 연결 실패", Toast.LENGTH_SHORT).show();
                        loadCart();
                    }
                });
    }

    /** DELETE /cart/items/{id} 삭제 후 재조회. */
    private void deleteItem(long itemId) {
        cartApi.deleteItem(itemId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    loadCart();
                } else {
                    Toast.makeText(CartActivity.this, "삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Toast.makeText(CartActivity.this, "네트워크 연결 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
