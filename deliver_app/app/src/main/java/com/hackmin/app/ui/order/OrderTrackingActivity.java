package com.hackmin.app.ui.order;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hackmin.app.R;
import com.hackmin.app.data.model.order.OrderDto;
import com.hackmin.app.data.model.order.OrderItemDto;
import com.hackmin.app.network.ApiClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrderTrackingActivity extends AppCompatActivity {

    // 상태 인덱스: 0=점주확인대기, 1=주문접수, 2=조리중, 3=배달중, 4=배달완료, -1=주문취소
    private int currentStatusIndex = 0;

    // ===== [C] START: 주문추적 GET /orders/{id} 연동 =====
    private long orderId = -1;
    // ===== [C] END =====

    private View[] dots;
    private View[] lines;

    private TextView tvCurrentStatus, tvRestaurantName, tvOrderItemsSummary, tvDeliveryAddress, tvCancelledBanner;
    private View containerProgress;
    private Button btnCancelOrder;
    private ImageButton btnBack;

    private final String[] statusLabels = {"점주확인대기", "주문접수", "조리중", "배달중", "배달완료"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_tracking);

        initViews();

        btnBack.setOnClickListener(v -> finish());

        // ===== [C] START: 주문추적 GET /orders/{id} 연동 =====
        orderId = getIntent().getLongExtra("order_id", -1);

        btnCancelOrder.setOnClickListener(v -> cancelOrder());

        if (orderId <= 0) {
            Toast.makeText(this, "주문 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        loadOrder();
        // ===== [C] END =====
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvCancelledBanner = findViewById(R.id.tvCancelledBanner);
        containerProgress = findViewById(R.id.containerProgress);
        tvCurrentStatus = findViewById(R.id.tvCurrentStatus);
        tvRestaurantName = findViewById(R.id.tvRestaurantName);
        tvOrderItemsSummary = findViewById(R.id.tvOrderItemsSummary);
        tvDeliveryAddress = findViewById(R.id.tvDeliveryAddress);
        btnCancelOrder = findViewById(R.id.btnCancelOrder);

        dots = new View[]{
                findViewById(R.id.dot1), findViewById(R.id.dot2),
                findViewById(R.id.dot3), findViewById(R.id.dot4), findViewById(R.id.dot5)
        };
        lines = new View[]{
                findViewById(R.id.line1), findViewById(R.id.line2),
                findViewById(R.id.line3), findViewById(R.id.line4)
        };
    }

    // ===== [C] START: 주문추적 GET /orders/{id} 연동 =====
    private void loadOrder() {
        ApiClient.orderApi(this).getOrder(orderId)
                .enqueue(new Callback<OrderDto>() {
                    @Override
                    public void onResponse(Call<OrderDto> call, Response<OrderDto> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            bindOrder(response.body());
                        } else {
                            Toast.makeText(OrderTrackingActivity.this,
                                    "주문 조회 실패", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<OrderDto> call, Throwable t) {
                        Toast.makeText(OrderTrackingActivity.this,
                                "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void bindOrder(OrderDto order) {
        // 목록 응답에 식당명이 없으므로 주문번호를 상단에 표시
        tvRestaurantName.setText(order.getOrderNumber() != null
                ? order.getOrderNumber() : ("주문 #" + order.getId()));
        tvOrderItemsSummary.setText(buildItemsSummary(order));
        tvDeliveryAddress.setText(buildAddress(order));

        currentStatusIndex = statusToIndex(order.getStatus());
        renderStatus();
    }

    private String buildItemsSummary(OrderDto order) {
        if (order.getItems() == null || order.getItems().isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        for (OrderItemDto item : order.getItems()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(item.getMenuName()).append(" x").append(item.getQuantity());
        }
        return sb.toString();
    }

    private String buildAddress(OrderDto order) {
        String addr = order.getAddress() != null ? order.getAddress() : "";
        if (order.getAddressDetail() != null && !order.getAddressDetail().isEmpty()) {
            addr = addr.isEmpty() ? order.getAddressDetail() : addr + " " + order.getAddressDetail();
        }
        return addr.isEmpty() ? "-" : addr;
    }

    /** 서버 상태코드 → 진행바 인덱스 (취소/거절은 -1) */
    private int statusToIndex(String status) {
        if (status == null) return 0;
        switch (status) {
            case "pending":
            case "placed":     return 0; // 점주확인대기
            case "accepted":   return 1; // 주문접수
            case "cooking":
            case "cooked":     return 2; // 조리중
            case "delivering": return 3; // 배달중
            case "delivered":  return 4; // 배달완료
            case "cancelled":
            case "rejected":   return -1; // 주문취소
            default:           return 0;
        }
    }

    private void cancelOrder() {
        ApiClient.orderApi(this).cancelOrder(orderId)
                .enqueue(new Callback<OrderDto>() {
                    @Override
                    public void onResponse(Call<OrderDto> call, Response<OrderDto> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Toast.makeText(OrderTrackingActivity.this,
                                    "주문이 취소되었습니다.", Toast.LENGTH_SHORT).show();
                            bindOrder(response.body());
                        } else {
                            Toast.makeText(OrderTrackingActivity.this,
                                    "현재 상태에서는 취소할 수 없습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<OrderDto> call, Throwable t) {
                        Toast.makeText(OrderTrackingActivity.this,
                                "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_LONG).show();
                    }
                });
    }
    // ===== [C] END =====

    private void renderStatus() {
        if (currentStatusIndex == -1) {
            // 주문취소 상태
            tvCancelledBanner.setVisibility(View.VISIBLE);
            containerProgress.setVisibility(View.GONE);
            btnCancelOrder.setVisibility(View.GONE);
            return;
        }

        // ===== [C] START: 취소상태 복귀 시 진행바 재표시 (재조회 대응) =====
        tvCancelledBanner.setVisibility(View.GONE);
        containerProgress.setVisibility(View.VISIBLE);
        // ===== [C] END =====

        tvCurrentStatus.setText(statusLabels[currentStatusIndex]);

        for (int i = 0; i < dots.length; i++) {
            dots[i].setBackgroundColor(i <= currentStatusIndex
                    ? 0xFF5C2D91 // 진행된 단계: 보라색
                    : 0xFFBDBDBD); // 아직 안된 단계: 회색
        }
        for (int i = 0; i < lines.length; i++) {
            lines[i].setBackgroundColor(i < currentStatusIndex
                    ? 0xFF5C2D91
                    : 0xFFBDBDBD);
        }

        // 배달중(3) 이후로는 취소 불가 처리 (배달완료 포함)
        btnCancelOrder.setVisibility(currentStatusIndex >= 3 ? View.GONE : View.VISIBLE);
    }
}
