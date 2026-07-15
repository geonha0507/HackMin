package com.hackmin.app.ui.cart;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.model.CartItem;

import java.util.ArrayList;
import java.util.List;

public class CartActivity extends AppCompatActivity {

    private RecyclerView rvCartItems;
    private TextView tvDeliveryFee, tvTotalPrice;
    private Button btnOrder;

    private List<CartItem> cartItemList;
    private CartItemAdapter adapter;

    // TODO: 더미데이터 - 실제 API 연동 시 교체 필요
    private int deliveryFee = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart_main);

        initViews();
        loadDummyCartItems();
        setupRecyclerView();
        updateSummary();

        btnOrder.setOnClickListener(v -> {
            // TODO: 주문서 작성 화면(OrderActivity)으로 이동 예정
        });
    }

    private void initViews() {
        rvCartItems = findViewById(R.id.rvCartItems);
        tvDeliveryFee = findViewById(R.id.tvDeliveryFee);
        tvTotalPrice = findViewById(R.id.tvTotalPrice);
        btnOrder = findViewById(R.id.btnOrder);
    }

    // TODO: 더미데이터 - 실제 API 연동 시 GET /cart 응답으로 교체 필요
    private void loadDummyCartItems() {
        cartItemList = new ArrayList<>();
        cartItemList.add(new CartItem(1, "후라이드치킨", "옵션: 순살", 15000, 1, ""));
        cartItemList.add(new CartItem(2, "콜라 1.25L", "", 2000, 1, ""));
    }

    private void setupRecyclerView() {
        adapter = new CartItemAdapter(cartItemList, new CartItemAdapter.OnCartItemActionListener() {
            @Override
            public void onQuantityChanged(CartItem item, int newQuantity) {
                updateSummary();
                // TODO: 실제로는 PUT /cart/items/{id} 호출 필요
            }

            @Override
            public void onDeleteItem(CartItem item) {
                cartItemList.remove(item);
                adapter.notifyDataSetChanged();
                updateSummary();
                // TODO: 실제로는 DELETE /cart/items/{id} 호출 필요
            }
        });
        rvCartItems.setLayoutManager(new LinearLayoutManager(this));
        rvCartItems.setAdapter(adapter);
    }

    private void updateSummary() {
        int subtotal = 0;
        for (CartItem item : cartItemList) {
            subtotal += item.getPrice() * item.getQuantity();
        }
        int total = subtotal + deliveryFee;

        tvDeliveryFee.setText(deliveryFee + "원");
        tvTotalPrice.setText(total + "원");
    }
}