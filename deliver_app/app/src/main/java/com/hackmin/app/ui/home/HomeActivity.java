package com.hackmin.app.ui.home;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.hackmin.app.R;
import com.hackmin.app.ui.cart.CartActivity;
import com.hackmin.app.ui.mypage.MyPageActivity;
import com.hackmin.app.ui.order.OrderHistoryActivity;

public class HomeActivity extends AppCompatActivity {

    private TextInputEditText etHomeSearch;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        prefs = getSharedPreferences("HackminPrefs", MODE_PRIVATE);

        etHomeSearch = findViewById(R.id.et_home_search);

        // 상단 아이콘 버튼
        ImageButton btnCart = findViewById(R.id.btn_cart);
        ImageButton btnMypage = findViewById(R.id.btn_mypage);

        if (btnCart != null) {
            btnCart.setOnClickListener(v ->
                    startActivity(new Intent(this, CartActivity.class)));
        }
        if (btnMypage != null) {
            btnMypage.setOnClickListener(v ->
                    startActivity(new Intent(this, MyPageActivity.class)));
        }

        // 카테고리 클릭 리스너
        setupCategoryListeners();
    }

    private void setupCategoryListeners() {
        int[] categoryIds = {
                R.id.category_chinese, R.id.category_chicken,
                R.id.category_cafe, R.id.category_stew, R.id.category_korean
        };
        String[] categoryNames = {"중식", "치킨", "카페", "찌개", "한식"};

        for (int i = 0; i < categoryIds.length; i++) {
            final String name = categoryNames[i];
            findViewById(categoryIds[i]).setOnClickListener(v ->
                    Toast.makeText(this, name + " 카테고리 선택", Toast.LENGTH_SHORT).show()
            );
        }
    }
}
