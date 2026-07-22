package com.hackmin.app.ui.order;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.order.OrderDto;
import com.hackmin.app.data.model.order.OrderItemDto;
import com.hackmin.app.data.model.order.OrderSummary;
import com.hackmin.app.data.model.restaurant.RestaurantDetailDto;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.ui.common.BottomNav;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrderHistoryActivity extends AppCompatActivity {

    private RecyclerView rvOrderHistory;
    private ImageButton btnBack;

    // ===== [C] START: 주문내역 GET /me/orders 연동 =====
    private OrderHistoryAdapter adapter;
    private final List<OrderSummary> orderList = new ArrayList<>();
    /** 음식점 id → 이름 캐시 (주문 응답엔 id만 오므로 상세조회로 보강) */
    private final Map<Long, String> restaurantNameCache = new HashMap<>();
    // ===== [C] END =====

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_history);

        rvOrderHistory = findViewById(R.id.rvOrderHistory);
        btnBack = findViewById(R.id.btnBack);

        rvOrderHistory.setLayoutManager(new LinearLayoutManager(this));

        // ===== [C] START: 주문내역 GET /me/orders 연동 =====
        adapter = new OrderHistoryAdapter(orderList, order -> {
            // 주문내역 항목 클릭 → 주문추적(상태) 화면으로 orderId 전달
            Intent intent = new Intent(this, OrderTrackingActivity.class);
            intent.putExtra("order_id", (long) order.getId());
            startActivity(intent);
        });
        rvOrderHistory.setAdapter(adapter);

        loadMyOrders();
        // ===== [C] END =====

        btnBack.setOnClickListener(v -> finish());
        BottomNav.setup(this, BottomNav.Tab.ORDERS);
    }

    // ===== [C] START: 주문내역 GET /me/orders 연동 =====
    private void loadMyOrders() {
        // status=null(전체), page=1 — 토큰/모드는 SessionManager가 자동 주입
        ApiClient.orderApi(this).getMyOrders(null, 1)
                .enqueue(new Callback<PagedResponse<OrderDto>>() {
                    @Override
                    public void onResponse(Call<PagedResponse<OrderDto>> call,
                                           Response<PagedResponse<OrderDto>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getResults() != null) {
                            orderList.clear();
                            for (OrderDto dto : response.body().getResults()) {
                                orderList.add(toSummary(dto));
                            }
                            adapter.notifyDataSetChanged();
                            resolveRestaurantNames();
                            if (orderList.isEmpty()) {
                                Toast.makeText(OrderHistoryActivity.this,
                                        "주문 내역이 없습니다.", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(OrderHistoryActivity.this,
                                    "주문 내역을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<PagedResponse<OrderDto>> call, Throwable t) {
                        Toast.makeText(OrderHistoryActivity.this,
                                "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * OrderDto(서버) → OrderSummary(목록 표시용) 변환.
     * 응답엔 식당명이 없고 restaurant(id)만 오므로, 식당명은 우선 임시값을 넣고
     * {@link #resolveRestaurantNames()}에서 상세조회로 보강한다. 메뉴는 items에서 바로 구성.
     */
    private OrderSummary toSummary(OrderDto dto) {
        long restaurantId = dto.getRestaurant() != null ? dto.getRestaurant() : -1L;
        String cachedName = restaurantNameCache.get(restaurantId);
        String name = cachedName != null ? cachedName : "주문 #" + dto.getId();
        String date = dto.getCreatedAt() != null && dto.getCreatedAt().length() >= 10
                ? dto.getCreatedAt().substring(0, 10)
                : "";
        return new OrderSummary((int) dto.getId(), restaurantId, name,
                buildMenuSummary(dto), date, dto.getTotal(), statusLabel(dto.getStatus()),
                firstItemImage(dto));
    }

    /** 주문 대표 썸네일: 이미지가 있는 첫 메뉴 항목의 사진 URL(없으면 null). */
    private String firstItemImage(OrderDto dto) {
        if (dto.getItems() == null) return null;
        for (OrderItemDto item : dto.getItems()) {
            String img = item.getMenuImage();
            if (img != null && !img.trim().isEmpty()) return img;
        }
        return null;
    }

    /** 주문 항목들을 "메뉴명 N개, 메뉴명 …" 형태로 요약. */
    private String buildMenuSummary(OrderDto dto) {
        if (dto.getItems() == null || dto.getItems().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (OrderItemDto item : dto.getItems()) {
            if (item.getMenuName() == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(item.getMenuName());
            if (item.getQuantity() > 1) sb.append(" ").append(item.getQuantity()).append("개");
        }
        return sb.toString();
    }

    /** 주문들에 등장한 음식점 id를 상세조회해 이름을 채운다(중복 id는 한 번만 조회, 캐시). */
    private void resolveRestaurantNames() {
        applyCachedNames();
        Set<Long> pending = new HashSet<>();
        for (OrderSummary s : orderList) {
            long rid = s.getRestaurantId();
            if (rid > 0 && !restaurantNameCache.containsKey(rid)) {
                pending.add(rid);
            }
        }
        for (Long rid : pending) {
            ApiClient.restaurantApi(this).getRestaurantDetail(rid)
                    .enqueue(new Callback<RestaurantDetailDto>() {
                        @Override
                        public void onResponse(Call<RestaurantDetailDto> call,
                                               Response<RestaurantDetailDto> response) {
                            if (response.isSuccessful() && response.body() != null
                                    && response.body().getName() != null) {
                                restaurantNameCache.put(rid, response.body().getName());
                                applyCachedNames();
                            }
                        }

                        @Override
                        public void onFailure(Call<RestaurantDetailDto> call, Throwable t) {
                            // 이름 보강 실패는 무시(임시 제목 유지).
                        }
                    });
        }
    }

    /** 캐시에 있는 음식점 이름을 목록에 반영. */
    private void applyCachedNames() {
        boolean changed = false;
        for (OrderSummary s : orderList) {
            String name = restaurantNameCache.get(s.getRestaurantId());
            if (name != null && !name.equals(s.getRestaurantName())) {
                s.setRestaurantName(name);
                changed = true;
            }
        }
        if (changed) adapter.notifyDataSetChanged();
    }

    /** 서버 상태코드 → 한글 라벨 */
    private String statusLabel(String status) {
        if (status == null) return "-";
        switch (status) {
            case "pending":    return "결제대기";
            case "placed":     return "점주확인대기";
            case "accepted":   return "주문접수";
            case "cooking":    return "조리중";
            case "cooked":     return "조리완료";
            case "delivering": return "배달중";
            case "delivered":  return "배달완료";
            case "rejected":   return "주문거절";
            case "cancelled":  return "주문취소";
            default:           return status;
        }
    }
    // ===== [C] END =====
}
