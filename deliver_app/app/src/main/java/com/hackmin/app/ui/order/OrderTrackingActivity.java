package com.hackmin.app.ui.order;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.hackmin.app.R;
import com.hackmin.app.data.model.order.OrderDto;
import com.hackmin.app.data.model.order.OrderItemDto;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.network.OrderWebSocketClient;
import com.hackmin.app.network.SessionManager;
import com.hackmin.app.ui.home.HomeActivity;
import com.hackmin.app.ui.review.WriteReviewActivity;
import com.hackmin.app.security.ReceiptSigner;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrderTrackingActivity extends com.hackmin.app.ui.common.BaseActivity {

    private static final String TAG = "OrderTracking";

    // 상태 인덱스: 0=점주확인대기, 1=주문접수, 2=조리중, 3=배달중, 4=배달완료, -1=주문취소
    private int currentStatusIndex = 0;

    private long orderId = -1;

    /** 리뷰 작성 진입에 필요한 식당 id / 표시용 이름 (주문 조회 후 채워짐) */
    private long restaurantId = -1;
    private String restaurantName;

    private View[] dots;
    private View[] lines;

    private TextView tvCurrentStatus, tvRestaurantName, tvOrderItemsSummary, tvDeliveryAddress, tvCancelledBanner;
    private View containerProgress;
    private Button btnCancelOrder, btnGoHome, btnWriteReview, btnConfirmReceipt;
    private ImageButton btnBack;

    /** 취소/거절 배너 문구 구분을 위해 최근 조회한 서버 상태 문자열을 보관. */
    private String lastStatus;

    private final String[] statusLabels = {"점주확인대기", "주문접수", "조리중", "배달중", "배달완료"};

    // ===== WebSocket 실시간 업데이트 =====
    private OrderWebSocketClient wsClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_tracking);

        initViews();

        btnBack.setOnClickListener(v -> finish());
        btnGoHome.setOnClickListener(v -> goHome());
        btnWriteReview.setOnClickListener(v -> goWriteReview());
        btnConfirmReceipt.setOnClickListener(v -> confirmReceipt());

        orderId = getIntent().getLongExtra("order_id", -1);

        btnCancelOrder.setOnClickListener(v -> cancelOrder());

        if (orderId <= 0) {
            Toast.makeText(this, "주문 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        loadOrder();
    }

    @Override
    protected void onStart() {
        super.onStart();
        connectWebSocket();
    }

    @Override
    protected void onStop() {
        super.onStop();
        disconnectWebSocket();
    }

    // ===== WebSocket 연결/해제 =====

    private void connectWebSocket() {
        if (orderId <= 0) return;

        String token = SessionManager.getInstance(this).getAccessToken();
        if (token == null || token.isEmpty()) {
            Log.w(TAG, "No access token — WebSocket skipped");
            return;
        }

        wsClient = new OrderWebSocketClient(orderId, token, (status, statusDisplay) -> {
            Log.d(TAG, "WS status update: " + status + " (" + statusDisplay + ")");
            lastStatus = status;
            currentStatusIndex = statusToIndex(status);
            renderStatus();
            Toast.makeText(this, "주문 상태: " + (statusDisplay != null ? statusDisplay : status),
                    Toast.LENGTH_SHORT).show();
        });
        wsClient.connect();
    }

    private void disconnectWebSocket() {
        if (wsClient != null) {
            wsClient.disconnect();
            wsClient = null;
        }
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
        btnGoHome = findViewById(R.id.btnGoHome);
        btnWriteReview = findViewById(R.id.btnWriteReview);
        btnConfirmReceipt = findViewById(R.id.btnConfirmReceipt);

        dots = new View[]{
                findViewById(R.id.dot1), findViewById(R.id.dot2),
                findViewById(R.id.dot3), findViewById(R.id.dot4), findViewById(R.id.dot5)
        };
        lines = new View[]{
                findViewById(R.id.line1), findViewById(R.id.line2),
                findViewById(R.id.line3), findViewById(R.id.line4)
        };
    }

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
        String title = order.getOrderNumber() != null
                ? order.getOrderNumber() : ("주문 #" + order.getId());
        tvRestaurantName.setText(title);
        tvOrderItemsSummary.setText(buildItemsSummary(order));
        tvDeliveryAddress.setText(buildAddress(order));

        restaurantId = order.getRestaurant() != null ? order.getRestaurant() : -1;
        restaurantName = title;

        lastStatus = order.getStatus();
        currentStatusIndex = statusToIndex(order.getStatus());
        renderStatus();
    }

    private void goWriteReview() {
        if (restaurantId < 0) {
            Toast.makeText(this, "리뷰를 작성할 수 없는 주문입니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(WriteReviewActivity.newIntent(this, restaurantId, orderId, restaurantName));
    }

    private void goHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
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
            case "placed":     return 0;
            case "accepted":   return 1;
            case "cooking":
            case "cooked":     return 2;
            case "delivering": return 3;
            case "delivered":  return 4;
            case "cancelled":
            case "rejected":   return -1;
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

    private void renderStatus() {
        if (currentStatusIndex == -1) {
            tvCancelledBanner.setText("rejected".equals(lastStatus)
                    ? "이 주문은 점주에 의해 거절되었습니다"
                    : "이 주문은 취소되었습니다");
            tvCancelledBanner.setVisibility(View.VISIBLE);
            containerProgress.setVisibility(View.GONE);
            btnCancelOrder.setVisibility(View.GONE);
            btnWriteReview.setVisibility(View.GONE);
            btnConfirmReceipt.setVisibility(View.GONE);
            return;
        }

        tvCancelledBanner.setVisibility(View.GONE);
        containerProgress.setVisibility(View.VISIBLE);

        tvCurrentStatus.setText(statusLabels[currentStatusIndex]);

        for (int i = 0; i < dots.length; i++) {
            dots[i].setBackgroundColor(i <= currentStatusIndex
                    ? 0xFFFF6F61
                    : 0xFFBDBDBD);
        }
        for (int i = 0; i < lines.length; i++) {
            lines[i].setBackgroundColor(i < currentStatusIndex
                    ? 0xFFFF6F61
                    : 0xFFBDBDBD);
        }

        btnCancelOrder.setVisibility(currentStatusIndex >= 3 ? View.GONE : View.VISIBLE);
        btnWriteReview.setVisibility(currentStatusIndex == 4 ? View.VISIBLE : View.GONE);
        btnConfirmReceipt.setVisibility(currentStatusIndex == 4 ? View.VISIBLE : View.GONE);
    }

    /**
     * [방어 ⑩] 고객 수령확인 — 네이티브 서명(ReceiptSigner.signReceipt → Keystore)을 붙여 서버로 전송.
     * 배달완료 상태에서만 노출. 백그라운드 스레드에서 키 등록 → 서명 → 호출을 수행한다.
     */
    private void confirmReceipt() {
        btnConfirmReceipt.setEnabled(false);
        new Thread(() -> {
            String msg;
            try {
                // 1) Keystore 키 확보 + 공개키 등록 (idempotent)
                String pem = ReceiptSigner.ensurePublicKeyPem();
                String keyId = ReceiptSigner.keyId();
                Map<String, String> reg = new HashMap<>();
                reg.put("key_id", keyId);
                reg.put("public_key_pem", pem);
                ApiClient.orderApi(this).registerReceiptKey(reg).execute();

                // 2) canonical 서명 (네이티브 signReceipt → Keystore). 서버 common/txnsig 규약과 동일.
                long ts = System.currentTimeMillis();
                String nonce = UUID.randomUUID().toString();
                String path = "/api/v1/orders/" + orderId + "/confirm-receipt";
                String canonical = "POST\n" + path + "\n" + ts + "\n" + nonce + "\n";
                String sig = ReceiptSigner.signB64(canonical.getBytes(StandardCharsets.UTF_8));

                // 3) 수령확인 호출 (서명 헤더 첨부)
                Map<String, String> h = new HashMap<>();
                h.put("X-Receipt-Ts", String.valueOf(ts));
                h.put("X-Receipt-Nonce", nonce);
                h.put("X-Receipt-Sig", sig);
                h.put("X-Key-Id", keyId);
                Response<ResponseBody> resp = ApiClient.orderApi(this).confirmReceipt(orderId, h).execute();
                msg = resp.isSuccessful()
                        ? "수령확인 완료 — 정산이 확정되었습니다."
                        : "수령확인 실패 (서명/상태 확인, code=" + resp.code() + ")";
            } catch (Throwable t) {
                msg = "수령확인 오류: " + t.getMessage();
            }
            final String out = msg;
            runOnUiThread(() -> {
                btnConfirmReceipt.setEnabled(true);
                Toast.makeText(this, out, Toast.LENGTH_LONG).show();
            });
        }).start();
    }
}
