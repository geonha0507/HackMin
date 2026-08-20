package com.hackmin.connect.ui.delivery;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.hackmin.connect.R;
import com.hackmin.connect.data.model.rider.DeliveryDetailDto;
import com.hackmin.connect.data.model.rider.DeliveryStatusRequest;
import com.hackmin.connect.network.ApiClient;
import com.hackmin.connect.ui.common.BaseActivity;
import com.hackmin.connect.ui.common.BottomNav;
import com.hackmin.connect.ui.common.ClickGuard;
import com.hackmin.connect.util.ConnectFormat;
import com.hackmin.connect.util.DeliveryFee;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 배달 상세 — 픽업지(가게)·전달지(고객) 정보와 진행 단계 표시,
 * 하단 버튼으로 상태를 한 단계씩 진행한다:
 * 신규 콜(assigned) → 픽업 완료(picked_up) → 배달 중(delivering) → 배달 완료(delivered).
 */
public class DeliveryDetailActivity extends BaseActivity {

    public static final String EXTRA_DELIVERY_ID = "delivery_id";
    public static final String EXTRA_RESTAURANT = "restaurant";
    public static final String EXTRA_TOTAL = "total";

    private long deliveryId;
    private DeliveryDetailDto current;

    private TextView tvStatus, tvOrderNumber, tvRestaurant, tvTotal, tvFee,
            tvCustomer, tvAddress, tvRequestNote;
    private TextView[] stepLabels;
    private View[] stepDots;
    private Button btnAction, btnCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivery_detail);
        BottomNav.setup(this, BottomNav.Tab.DELIVERY);

        deliveryId = getIntent().getLongExtra(EXTRA_DELIVERY_ID, -1L);

        tvStatus = findViewById(R.id.tv_status);
        tvOrderNumber = findViewById(R.id.tv_order_number);
        tvRestaurant = findViewById(R.id.tv_restaurant);
        tvTotal = findViewById(R.id.tv_total);
        tvFee = findViewById(R.id.tv_fee);
        tvCustomer = findViewById(R.id.tv_customer);
        tvAddress = findViewById(R.id.tv_address);
        tvRequestNote = findViewById(R.id.tv_request_note);
        btnAction = findViewById(R.id.btn_action);
        btnCall = findViewById(R.id.btn_call);

        stepDots = new View[]{
                findViewById(R.id.step_dot_1), findViewById(R.id.step_dot_2),
                findViewById(R.id.step_dot_3), findViewById(R.id.step_dot_4)};
        stepLabels = new TextView[]{
                findViewById(R.id.step_label_1), findViewById(R.id.step_label_2),
                findViewById(R.id.step_label_3), findViewById(R.id.step_label_4)};

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 목록에서 넘겨받은 가게 이름/주문금액(상세 API에는 없음).
        String restaurant = getIntent().getStringExtra(EXTRA_RESTAURANT);
        tvRestaurant.setText(restaurant == null || restaurant.isEmpty() ? "가게 미지정" : restaurant);
        tvTotal.setText("주문금액 " + ConnectFormat.won(getIntent().getIntExtra(EXTRA_TOTAL, 0)));
        tvFee.setText("배달료 " + ConnectFormat.won(DeliveryFee.PER_DELIVERY));

        btnCall.setOnClickListener(v -> {
            if (!ClickGuard.allow()) return;
            if (current == null || current.getPhone() == null || current.getPhone().isEmpty()) {
                Toast.makeText(this, "연락처 정보가 없어요.", Toast.LENGTH_SHORT).show();
                return;
            }
            // ACTION_DIAL은 권한 불필요(다이얼러만 연다).
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + current.getPhone())));
        });

        btnAction.setOnClickListener(v -> advanceStatus());
        load();
    }

    private void load() {
        ApiClient.riderApi(this).getDelivery(deliveryId).enqueue(new Callback<DeliveryDetailDto>() {
            @Override
            public void onResponse(Call<DeliveryDetailDto> call, Response<DeliveryDetailDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    current = response.body();
                    render();
                } else {
                    Toast.makeText(DeliveryDetailActivity.this,
                            "배달 정보를 찾을 수 없어요.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onFailure(Call<DeliveryDetailDto> call, Throwable t) {
                Toast.makeText(DeliveryDetailActivity.this,
                        "네트워크 연결 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void render() {
        String status = current.getStatus() == null ? "" : current.getStatus();

        DeliveryAdapter.bindStatus(tvStatus, status);
        tvOrderNumber.setText("주문 " + (current.getOrderNumber() == null ? "-" : current.getOrderNumber()));
        tvCustomer.setText(emptyDash(current.getCustomer()));
        String addr = emptyDash(current.getAddress());
        if (current.getAddressDetail() != null && !current.getAddressDetail().isEmpty()) {
            addr += " " + current.getAddressDetail();
        }
        tvAddress.setText(addr);
        tvRequestNote.setText(current.getRequestNote() == null || current.getRequestNote().isEmpty()
                ? "요청사항 없음" : current.getRequestNote());

        // 진행 단계(접수 → 픽업 → 배달 중 → 완료) 하이라이트.
        int step = stepOf(status);
        for (int i = 0; i < 4; i++) {
            boolean on = i <= step;
            stepDots[i].setBackgroundResource(on
                    ? R.drawable.bg_step_dot_on : R.drawable.bg_step_dot_off);
            stepLabels[i].setTextColor(getColor(on
                    ? R.color.coral_primary : R.color.text_secondary));
        }

        switch (status) {
            case "assigned":
                btnAction.setVisibility(View.VISIBLE);
                btnAction.setText("콜 수락하고 픽업 완료");
                break;
            case "picked_up":
                btnAction.setVisibility(View.VISIBLE);
                btnAction.setText("배달 시작");
                break;
            case "delivering":
                btnAction.setVisibility(View.VISIBLE);
                btnAction.setText("배달 완료");
                break;
            default: // delivered 등
                btnAction.setVisibility(View.GONE);
        }
    }

    /** 현재 상태의 다음 단계로 전이한다(서버가 미배정 건은 이때 본인에게 배정). */
    private void advanceStatus() {
        if (current == null || !ClickGuard.allow()) return;
        String next;
        switch (current.getStatus() == null ? "" : current.getStatus()) {
            case "assigned":
                next = "picked_up";
                break;
            case "picked_up":
                next = "delivering";
                break;
            case "delivering":
                next = "delivered";
                break;
            default:
                return;
        }
        btnAction.setEnabled(false);
        ApiClient.riderApi(this).updateDeliveryStatus(deliveryId, new DeliveryStatusRequest(next))
                .enqueue(new Callback<DeliveryDetailDto>() {
            @Override
            public void onResponse(Call<DeliveryDetailDto> call, Response<DeliveryDetailDto> response) {
                btnAction.setEnabled(true);
                if (response.isSuccessful() && response.body() != null) {
                    current = response.body();
                    if ("delivered".equals(current.getStatus())) {
                        Toast.makeText(DeliveryDetailActivity.this,
                                "배달 완료! 배달료 " + ConnectFormat.won(DeliveryFee.PER_DELIVERY)
                                        + "이 적립됐어요.", Toast.LENGTH_LONG).show();
                    }
                    render();
                } else {
                    Toast.makeText(DeliveryDetailActivity.this,
                            "상태 변경에 실패했어요.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<DeliveryDetailDto> call, Throwable t) {
                btnAction.setEnabled(true);
                Toast.makeText(DeliveryDetailActivity.this,
                        "네트워크 연결 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static int stepOf(String status) {
        switch (status) {
            case "picked_up": return 1;
            case "delivering": return 2;
            case "delivered": return 3;
            default: return 0; // assigned
        }
    }

    private static String emptyDash(String s) {
        return s == null || s.isEmpty() ? "-" : s;
    }
}
