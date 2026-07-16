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
import com.hackmin.app.data.model.order.OrderSummary;
import com.hackmin.app.network.ApiClient;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrderHistoryActivity extends AppCompatActivity {

    private RecyclerView rvOrderHistory;
    private ImageButton btnBack;

    // ===== [C] START: 주문내역 GET /me/orders 연동 =====
    private OrderHistoryAdapter adapter;
    private final List<OrderSummary> orderList = new ArrayList<>();
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
     * 참고: /me/orders 응답에는 식당명이 없고 restaurant(id)만 오므로,
     *       목록 제목은 주문번호를 사용한다. (식당명 필요 시 상세조회에서 보강)
     */
    private OrderSummary toSummary(OrderDto dto) {
        String title = dto.getOrderNumber() != null ? dto.getOrderNumber() : ("주문 #" + dto.getId());
        String date = dto.getCreatedAt() != null && dto.getCreatedAt().length() >= 10
                ? dto.getCreatedAt().substring(0, 10)
                : "";
        return new OrderSummary((int) dto.getId(), title, date, dto.getTotal(), statusLabel(dto.getStatus()));
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
