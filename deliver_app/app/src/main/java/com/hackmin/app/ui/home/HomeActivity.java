package com.hackmin.app.ui.home;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.hackmin.app.R;

public class HomeActivity extends AppCompatActivity {

    private TextInputEditText etHomeSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        etHomeSearch = findViewById(R.id.et_home_search);

        // ==========================
        // 상단 버튼
        // ==========================

        ImageButton btnNotification = findViewById(R.id.btn_notification);
        ImageButton btnCart = findViewById(R.id.btn_cart);
        ImageButton btnMyPage = findViewById(R.id.btn_mypage);

        btnNotification.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("📢 공지사항")
                .setMessage(
                        "HackMin 공지사항\n\n" +
                                "① 첫 주문 시 3,000원 할인\n\n" +
                                "② 신규 취약점 실습 콘텐츠 업데이트\n\n" +
                                "③ 현재 앱은 최신 버전입니다."
                )
                .setPositiveButton("확인", null)
                .show());

        btnCart.setOnClickListener(v ->
                Toast.makeText(this,
                        "장바구니가 비어있습니다.",
                        Toast.LENGTH_SHORT).show()
        );

        btnMyPage.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, com.hackmin.app.ui.mypage.MyPageActivity.class);
            startActivity(intent);
        });

        // ==========================
        // 카테고리
        // ==========================

        android.view.View catChinese = findViewById(R.id.category_chinese);
        android.view.View catChicken = findViewById(R.id.category_chicken);
        android.view.View catCafe = findViewById(R.id.category_cafe);
        android.view.View catStew = findViewById(R.id.category_stew);
        android.view.View catKorean = findViewById(R.id.category_korean);

        if (catChinese != null) catChinese.setOnClickListener(v ->
                Toast.makeText(this, "중식 카테고리 선택", Toast.LENGTH_SHORT).show());
        if (catChicken != null) catChicken.setOnClickListener(v ->
                Toast.makeText(this, "치킨 카테고리 선택", Toast.LENGTH_SHORT).show());
        if (catCafe != null) catCafe.setOnClickListener(v ->
                Toast.makeText(this, "카페 카테고리 선택", Toast.LENGTH_SHORT).show());
        if (catStew != null) catStew.setOnClickListener(v ->
                Toast.makeText(this, "찜, 탕 카테고리 선택", Toast.LENGTH_SHORT).show());
        if (catKorean != null) catKorean.setOnClickListener(v ->
                Toast.makeText(this, "한식 카테고리 선택", Toast.LENGTH_SHORT).show());

        // ==========================
        // 검색
        // ==========================

        etHomeSearch.setOnEditorActionListener((v, actionId, event) -> {

            String query = etHomeSearch.getText() != null
                    ? etHomeSearch.getText().toString().trim()
                    : "";

            if (!query.isEmpty()) {

                Toast.makeText(
                        this,
                        "\"" + query + "\" 검색",
                        Toast.LENGTH_SHORT
                ).show();

                // TODO 검색 화면 이동
            }

            return true;
        });

    }
}