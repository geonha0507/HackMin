package com.hackmin.app.ui.order;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.hackmin.app.R;

public class OrderTrackingActivity extends AppCompatActivity {

    // 상태 인덱스: 0=점주확인대기, 1=주문접수, 2=조리중, 3=배달중, 4=배달완료, -1=주문취소
    // TODO: 더미데이터 - 실제 API 연동 시 GET /orders/{id}/status 응답으로 교체 필요
    private int currentStatusIndex = 2; // 예: 지금은 "조리중" 상태로 더미 설정

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
        loadDummyOrderInfo();
        renderStatus();

        btnBack.setOnClickListener(v -> finish());

        btnCancelOrder.setOnClickListener(v -> {
            // TODO: 실제로는 POST /orders/{id}/cancel 호출 필요
        });
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

    // TODO: 더미데이터 - 실제 API 연동 시 GET /orders/{id} 응답으로 교체 필요
    private void loadDummyOrderInfo() {
        tvRestaurantName.setText("치킨왕집");
        tvOrderItemsSummary.setText("후라이드치킨 x1, 콜라 1.25L x1");
        tvDeliveryAddress.setText("서울시 강남구 테헤란로 123");
    }

    private void renderStatus() {
        if (currentStatusIndex == -1) {
            // 주문취소 상태
            tvCancelledBanner.setVisibility(View.VISIBLE);
            containerProgress.setVisibility(View.GONE);
            btnCancelOrder.setVisibility(View.GONE);
            return;
        }

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