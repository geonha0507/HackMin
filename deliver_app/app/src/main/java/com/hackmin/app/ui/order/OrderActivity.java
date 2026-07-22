package com.hackmin.app.ui.order;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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
import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.order.OrderCreateRequest;
import com.hackmin.app.data.model.order.OrderDto;
import com.hackmin.app.data.model.payment.PaymentCreateRequest;
import com.hackmin.app.data.model.payment.PaymentDto;
import com.hackmin.app.data.model.user.AddressDto;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;

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

        btnChangeAddress.setOnClickListener(v -> selectAddress());
        btnPay.setOnClickListener(v -> submitOrder());

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        loadCart();
    }

    private void initApi() {
        // 토큰/모드는 SessionManager가 자동 주입 — Context만 넘기면 된다.
        cartApi = ApiClient.cartApi(this);
        orderApi = ApiClient.orderApi(this);
        paymentApi = ApiClient.paymentApi(this);
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
            View row = getLayoutInflater()
                    .inflate(R.layout.item_order_line, containerOrderItems, false);
            ImageView iv = row.findViewById(R.id.ivLineThumb);
            TextView name = row.findViewById(R.id.tvLineName);
            TextView price = row.findViewById(R.id.tvLinePrice);

            ImageLoader.load(iv, item.getMenuImage());
            name.setText(item.getMenuName() + " x" + item.getQuantity());
            price.setText(item.getLineTotal() + "원");

            containerOrderItems.addView(row);
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
     * 저장된 배송지(GET /me/addresses)를 불러와 선택하게 한다.
     * 저장된 배송지가 없으면 직접 입력 다이얼로그로 폴백한다.
     */
    private void selectAddress() {
        ApiClient.userApi(this).getAddresses().enqueue(new Callback<PagedResponse<AddressDto>>() {
            @Override
            public void onResponse(@NonNull Call<PagedResponse<AddressDto>> call,
                                   @NonNull Response<PagedResponse<AddressDto>> response) {
                List<AddressDto> list = response.isSuccessful() && response.body() != null
                        ? response.body().getResults() : null;
                if (list == null || list.isEmpty()) {
                    // 저장된 배송지가 없으면 직접 입력.
                    showAddressInputDialog();
                    return;
                }
                showAddressPicker(list);
            }

            @Override
            public void onFailure(@NonNull Call<PagedResponse<AddressDto>> call, @NonNull Throwable t) {
                // 목록 조회 실패 시에도 최소한 직접 입력은 가능하게.
                showAddressInputDialog();
            }
        });
    }

    /** 저장 배송지 선택 다이얼로그(+ 직접 입력 항목). */
    private void showAddressPicker(List<AddressDto> list) {
        List<String> labels = new ArrayList<>();
        for (AddressDto a : list) {
            String label = (a.getLabel() != null && !a.getLabel().isEmpty() ? "[" + a.getLabel() + "] " : "")
                    + a.getAddress()
                    + (a.getDetail() != null && !a.getDetail().isEmpty() ? " " + a.getDetail() : "");
            labels.add(label);
        }
        labels.add("+ 직접 입력");
        String[] items = labels.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("배송지 선택")
                .setItems(items, (dialog, which) -> {
                    if (which == list.size()) {
                        showAddressInputDialog();
                        return;
                    }
                    AddressDto a = list.get(which);
                    selectedAddress = a.getAddress() != null ? a.getAddress() : "";
                    selectedAddressDetail = a.getDetail() != null ? a.getDetail() : "";
                    tvSelectedAddress.setText(
                            selectedAddress
                                    + (TextUtils.isEmpty(selectedAddressDetail) ? "" : " " + selectedAddressDetail));
                })
                .show();
    }

    /** 배송지 직접 입력 다이얼로그(저장 배송지가 없을 때 폴백). */
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
                } else if (response.code() == 400) {
                    // 최소 주문금액 미달 등 서버 검증 실패 → 서버 메시지 그대로 안내.
                    fail(extractErrorMessage(response, "최소 주문금액을 확인해주세요."));
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

    /** 에러 응답 본문({code,message})에서 message를 뽑아낸다. 실패 시 fallback 반환. */
    private String extractErrorMessage(Response<?> response, String fallback) {
        try {
            if (response.errorBody() != null) {
                com.hackmin.app.data.model.common.ApiErrorResponse err = new com.google.gson.Gson()
                        .fromJson(response.errorBody().string(),
                                com.hackmin.app.data.model.common.ApiErrorResponse.class);
                if (err != null && err.getMessage() != null && !err.getMessage().isEmpty()) {
                    return err.getMessage();
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
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
                    // 주문은 생성됐지만 결제 실패 — 같은 주문으로 재결제 제안.
                    offerPaymentRetry(order, "결제에 실패했습니다.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<PaymentDto> call, @NonNull Throwable t) {
                offerPaymentRetry(order, "결제 중 네트워크 오류가 발생했습니다.");
            }
        });
    }

    /**
     * 결제 실패 시 재시도 다이얼로그.
     * 주문은 이미 생성(PENDING)돼 있으므로, 주문을 다시 만들지 않고
     * 같은 주문에 대해 결제(payForOrder)만 재시도한다.
     */
    private void offerPaymentRetry(OrderDto order, String message) {
        // 재시도할 수 있도록 버튼/상태를 먼저 원복.
        submitting = false;
        btnPay.setEnabled(true);
        new AlertDialog.Builder(this)
                .setTitle("결제 실패")
                .setMessage(message + "\n주문번호 " + order.getId() + " 로 다시 결제할까요?")
                .setPositiveButton("결제 재시도", (d, w) -> {
                    submitting = true;
                    btnPay.setEnabled(false);
                    payForOrder(order);
                })
                .setNegativeButton("닫기", null)
                .setCancelable(false)
                .show();
    }

    private void fail(String message) {
        submitting = false;
        btnPay.setEnabled(true);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
