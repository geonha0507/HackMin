package com.hackmin.app.ui.order;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.hackmin.app.R;
import com.hackmin.app.data.api.CartApi;
import com.hackmin.app.data.api.OrderApi;
import com.hackmin.app.data.api.PaymentApi;
import com.hackmin.app.data.model.cart.CartDto;
import com.hackmin.app.data.model.cart.CartItemDto;
import com.hackmin.app.data.model.cart.CartSummaryDto;
import com.hackmin.app.data.model.order.OrderCreateRequest;
import com.hackmin.app.data.model.order.OrderDto;
import com.hackmin.app.data.model.payment.PaymentCreateRequest;
import com.hackmin.app.data.model.payment.PaymentDto;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.network.HackminMode;
import com.hackmin.app.network.HackminModeInterceptor;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrderActivity extends AppCompatActivity {

    /** OrderTrackingActivity 로 넘길 주문 id 인텐트 키. */
    public static final String EXTRA_ORDER_ID = "order_id";

    private TextView tvSelectedAddress, tvTotalPayment;
    private Button btnChangeAddress, btnPay;
    private EditText etRequestMessage;
    private RadioGroup rgPaymentMethod;
    private LinearLayout containerOrderItems;

    private CartApi cartApi;
    private OrderApi orderApi;
    private PaymentApi paymentApi;

    // 선택/입력된 배송지 (C의 저장 배송지 선택이 나오면 교체 예정)
    private String selectedAddress = "";
    private String selectedAddressDetail = "";

    private boolean submitting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_sheet);

        initApi();
        initViews();

        btnChangeAddress.setOnClickListener(v -> showAddressInputDialog());
        btnPay.setOnClickListener(v -> submitOrder());

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        loadCart();
    }

    private void initApi() {
        SharedPreferences prefs = getSharedPreferences("HackminPrefs", Context.MODE_PRIVATE);
        HackminModeInterceptor.ModeProvider mp = () -> HackminMode.SECURE;
        HackminModeInterceptor.TokenProvider tp = () -> prefs.getString("access_token", "");
        cartApi = ApiClient.cartApi(mp, tp);
        orderApi = ApiClient.orderApi(mp, tp);
        paymentApi = ApiClient.paymentApi(mp, tp);
    }

    private void initViews() {
        tvSelectedAddress = findViewById(R.id.tvSelectedAddress);
        tvTotalPayment = findViewById(R.id.tvTotalPayment);
        btnChangeAddress = findViewById(R.id.btnChangeAddress);
        btnPay = findViewById(R.id.btnPay);
        etRequestMessage = findViewById(R.id.etRequestMessage);
        rgPaymentMethod = findViewById(R.id.rgPaymentMethod);
        containerOrderItems = findViewById(R.id.containerOrderItems);
    }

    /** 주문 내역·금액은 서버 장바구니에서 가져온다. (주문 생성도 서버 장바구니 기준) */
    private void loadCart() {
        cartApi.getCart().enqueue(new Callback<CartDto>() {
            @Override
            public void onResponse(@NonNull Call<CartDto> call, @NonNull Response<CartDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    renderOrderItems(response.body());
                    loadSummary();
                } else {
                    Toast.makeText(OrderActivity.this, "장바구니를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<CartDto> call, @NonNull Throwable t) {
                Toast.makeText(OrderActivity.this, "네트워크 연결 실패 (서버 확인 필요)", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderOrderItems(CartDto cart) {
        containerOrderItems.removeAllViews();
        if (cart.getItems() == null) return;
        for (CartItemDto item : cart.getItems()) {
            TextView tv = new TextView(this);
            tv.setText(item.getMenuName() + " x" + item.getQuantity() + "   " + item.getLineTotal() + "원");
            tv.setPadding(0, 8, 0, 8);
            containerOrderItems.addView(tv);
        }
    }

    private void loadSummary() {
        cartApi.getCartSummary().enqueue(new Callback<CartSummaryDto>() {
            @Override
            public void onResponse(@NonNull Call<CartSummaryDto> call, @NonNull Response<CartSummaryDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    tvTotalPayment.setText(response.body().getTotal() + "원");
                }
            }

            @Override
            public void onFailure(@NonNull Call<CartSummaryDto> call, @NonNull Throwable t) {
                // 총액만 실패 — 목록은 표시됨. 조용히 무시.
            }
        });
    }

    /**
     * 배송지 직접 입력 다이얼로그(임시).
     * TODO: C의 GET /me/addresses(저장 배송지) 연동되면 선택형으로 교체.
     */
    private void showAddressInputDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, 0);

        EditText etAddr = new EditText(this);
        etAddr.setHint("주소 (예: 서울시 강남구 테헤란로 123)");
        etAddr.setText(selectedAddress);
        EditText etDetail = new EditText(this);
        etDetail.setHint("상세주소 (예: 101동 1001호)");
        etDetail.setText(selectedAddressDetail);
        box.addView(etAddr);
        box.addView(etDetail);

        new AlertDialog.Builder(this)
                .setTitle("배송지 입력")
                .setView(box)
                .setPositiveButton("확인", (d, w) -> {
                    selectedAddress = etAddr.getText().toString().trim();
                    selectedAddressDetail = etDetail.getText().toString().trim();
                    if (TextUtils.isEmpty(selectedAddress)) {
                        tvSelectedAddress.setText("배송지를 선택해주세요");
                    } else {
                        tvSelectedAddress.setText(
                                selectedAddress
                                        + (TextUtils.isEmpty(selectedAddressDetail) ? "" : " " + selectedAddressDetail));
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /** 결제하기: 주문 생성(POST /orders) → 결제(POST /payments) → 주문추적 이동. */
    private void submitOrder() {
        if (submitting) return;
        if (containerOrderItems.getChildCount() == 0) {
            Toast.makeText(this, "주문할 상품이 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(selectedAddress)) {
            Toast.makeText(this, "배송지를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        submitting = true;
        btnPay.setEnabled(false);

        String note = etRequestMessage.getText().toString().trim();
        OrderCreateRequest req = new OrderCreateRequest(selectedAddress, selectedAddressDetail, note);

        orderApi.createOrder(req).enqueue(new Callback<OrderDto>() {
            @Override
            public void onResponse(@NonNull Call<OrderDto> call, @NonNull Response<OrderDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    payForOrder(response.body());
                } else {
                    fail("주문 생성에 실패했습니다.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<OrderDto> call, @NonNull Throwable t) {
                fail("네트워크 연결 실패 (서버 확인 필요)");
            }
        });
    }

    private void payForOrder(OrderDto order) {
        // 라디오 선택 → 서버가 허용하는 결제수단 코드로 매핑.
        String method = rgPaymentMethod.getCheckedRadioButtonId() == R.id.rbEasyPay ? "easy_pay" : "card";
        // amount 는 생략 — 서버가 주문 총액을 사용(전달 시 정확히 일치해야 함).
        PaymentCreateRequest req = new PaymentCreateRequest(order.getId(), method);

        paymentApi.createPayment(req).enqueue(new Callback<PaymentDto>() {
            @Override
            public void onResponse(@NonNull Call<PaymentDto> call, @NonNull Response<PaymentDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(OrderActivity.this, "결제가 완료되었습니다.", Toast.LENGTH_SHORT).show();
                    Intent i = new Intent(OrderActivity.this, OrderTrackingActivity.class);
                    i.putExtra(EXTRA_ORDER_ID, order.getId());
                    startActivity(i);
                    finish();
                } else {
                    // 주문은 생성됐지만 결제 실패 — 주문 id 안내.
                    fail("결제에 실패했습니다. (주문번호 " + order.getId() + ")");
                }
            }

            @Override
            public void onFailure(@NonNull Call<PaymentDto> call, @NonNull Throwable t) {
                fail("결제 중 네트워크 오류가 발생했습니다. (주문번호 " + order.getId() + ")");
            }
        });
    }

    private void fail(String message) {
        submitting = false;
        btnPay.setEnabled(true);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
