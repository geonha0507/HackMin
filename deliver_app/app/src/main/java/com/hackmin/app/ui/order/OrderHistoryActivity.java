package com.hackmin.app.ui.order;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.model.order.OrderSummary;

import java.util.ArrayList;
import java.util.List;

public class OrderHistoryActivity extends AppCompatActivity {

    private RecyclerView rvOrderHistory;
    private ImageButton btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_history);

        rvOrderHistory = findViewById(R.id.rvOrderHistory);
        btnBack = findViewById(R.id.btnBack);

        rvOrderHistory.setLayoutManager(new LinearLayoutManager(this));
        rvOrderHistory.setAdapter(new OrderHistoryAdapter(loadDummyOrderHistory(), order -> {
            // TODO: 주문 상세 화면(OrderDetailActivity)으로 이동 예정, 실제로는 GET /orders/{id} 연동 필요
            Toast.makeText(this, order.getRestaurantName() + " 상세보기 (준비중)", Toast.LENGTH_SHORT).show();
        }));

        btnBack.setOnClickListener(v -> finish());
    }

    // TODO: 더미데이터 - 실제 API 연동 시 GET /me/orders 응답으로 교체 필요
    private List<OrderSummary> loadDummyOrderHistory() {
        List<OrderSummary> list = new ArrayList<>();
        list.add(new OrderSummary(1, "치킨왕집", "2026-07-10", 20000, "배달완료"));
        list.add(new OrderSummary(2, "피자나라", "2026-07-05", 32000, "배달완료"));
        list.add(new OrderSummary(3, "김밥천국", "2026-07-01", 8500, "주문취소"));
        return list;
    }
}