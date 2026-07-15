package com.hackmin.app.ui.order;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.hackmin.app.R;
import com.hackmin.app.data.model.cart.CartItem;

import java.util.ArrayList;
import java.util.List;

public class OrderActivity extends AppCompatActivity {

    private TextView tvSelectedAddress, tvTotalPayment;
    private Button btnChangeAddress, btnPay;
    private EditText etRequestMessage;
    private RadioGroup rgPaymentMethod;
    private LinearLayout containerOrderItems;

    private List<CartItem> orderItemList;

    // TODO: 더미데이터 - 실제 API 연동 시 GET /me/addresses 응답으로 교체 필요
    private final String[] dummyAddresses = {
            "서울시 강남구 테헤란로 123",
            "서울시 서초구 서초대로 45",
            "경기도 성남시 분당구 판교역로 6"
    };

    private int deliveryFee = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_sheet);

        initViews();
        loadDummyOrderItems();
        renderOrderItems();
        updateTotalPayment();

        btnChangeAddress.setOnClickListener(v -> showAddressPickerDialog());

        btnPay.setOnClickListener(v -> {
            // TODO: 실제로는 POST /orders 호출 필요 (주문 생성 후 결제 진행)
        });
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

    // TODO: 더미데이터 - 실제로는 장바구니(GET /cart)에서 넘어온 항목 사용 필요
    private void loadDummyOrderItems() {
        orderItemList = new ArrayList<>();
        orderItemList.add(new CartItem(1, "후라이드치킨", "옵션: 순살", 15000, 1, ""));
        orderItemList.add(new CartItem(2, "콜라 1.25L", "", 2000, 1, ""));
    }

    private void renderOrderItems() {
        containerOrderItems.removeAllViews();
        for (CartItem item : orderItemList) {
            TextView tv = new TextView(this);
            tv.setText(item.getMenuName() + " x" + item.getQuantity() + "   "
                    + (item.getPrice() * item.getQuantity()) + "원");
            tv.setPadding(0, 8, 0, 8);
            containerOrderItems.addView(tv);
        }
    }

    private void showAddressPickerDialog() {
        // TODO: 더미데이터 - 실제 API 연동 시 GET /me/addresses 응답으로 교체 필요
        new AlertDialog.Builder(this)
                .setTitle("배송지 선택")
                .setItems(dummyAddresses, (dialog, which) -> {
                    tvSelectedAddress.setText(dummyAddresses[which]);
                })
                .show();
    }

    private void updateTotalPayment() {
        int subtotal = 0;
        for (CartItem item : orderItemList) {
            subtotal += item.getPrice() * item.getQuantity();
        }
        int total = subtotal + deliveryFee;
        tvTotalPayment.setText(total + "원");
    }
}