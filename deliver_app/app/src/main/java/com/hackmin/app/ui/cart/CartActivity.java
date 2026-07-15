package com.hackmin.app.ui.cart;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.api.CartApi;
import com.hackmin.app.data.model.cart.CartDto;
import com.hackmin.app.data.model.cart.CartSummaryDto;
import com.hackmin.app.data.model.cart.UpdateCartItemRequest;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.network.HackminMode;
import com.hackmin.app.network.HackminModeInterceptor;
import com.hackmin.app.ui.order.OrderActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CartActivity extends AppCompatActivity {

    private RecyclerView rvCartItems;
    private TextView tvDeliveryFee, tvTotalPrice;
    private Button btnOrder;

    private CartItemAdapter adapter;
    private CartApi cartApi;

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
            startActivity(new Intent(this, OrderActivity.class));
        });

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 다른 화면에서 담고 돌아오는 경우를 위해 진입 시마다 서버 상태로 갱신.
        loadCart();
    }

    private void initApi() {
        SharedPreferences prefs = getSharedPreferences("HackminPrefs", Context.MODE_PRIVATE);
        HackminModeInterceptor.ModeProvider mp = () -> HackminMode.SECURE;
        HackminModeInterceptor.TokenProvider tp = () -> prefs.getString("access_token", "");
        cartApi = ApiClient.cartApi(mp, tp);
    }

    private void initViews() {
        rvCartItems = findViewById(R.id.rvCartItems);
        tvDeliveryFee = findViewById(R.id.tvDeliveryFee);
        tvTotalPrice = findViewById(R.id.tvTotalPrice);
        btnOrder = findViewById(R.id.btnOrder);
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
                    tvDeliveryFee.setText(s.getDeliveryFee() + "원");
                    tvTotalPrice.setText(s.getTotal() + "원");
                }
            }

            @Override
            public void onFailure(@NonNull Call<CartSummaryDto> call, @NonNull Throwable t) {
                // 목록은 이미 표시됨 — 요약만 실패한 경우 조용히 무시.
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
