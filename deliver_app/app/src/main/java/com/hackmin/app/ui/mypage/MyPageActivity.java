package com.hackmin.app.ui.mypage;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.hackmin.app.R;
import com.hackmin.app.data.api.UserApi;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.network.HackminMode;
import com.hackmin.app.network.HackminModeInterceptor;
import com.hackmin.app.ui.auth.LoginActivity;
import com.hackmin.app.ui.order.OrderHistoryActivity;

public class MyPageActivity extends AppCompatActivity {

    private TextView tvNickname, tvUsername, tvPhone;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mypage);

        prefs = getSharedPreferences("HackminPrefs", MODE_PRIVATE);

        // 뒤로가기
        findViewById(R.id.tv_mypage_back).setOnClickListener(v -> finish());

        // 프로필 영역
        tvNickname = findViewById(R.id.tv_mypage_nickname);
        tvUsername = findViewById(R.id.tv_mypage_username);
        tvPhone = findViewById(R.id.tv_mypage_phone);

        // 메뉴 항목들
        findViewById(R.id.menu_orders).setOnClickListener(v ->
                startActivity(new Intent(this, OrderHistoryActivity.class)));

        findViewById(R.id.menu_logout).setOnClickListener(v -> {
            prefs.edit().remove("access_token").apply();
            Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        // TODO: 나머지 메뉴 항목 클릭 리스너 구현
        int[] menuIds = {R.id.menu_addresses, R.id.menu_reviews,
                R.id.menu_coupons, R.id.menu_membership,
                R.id.menu_change_password, R.id.menu_withdraw};
        for (int id : menuIds) {
            findViewById(id).setOnClickListener(v ->
                    Toast.makeText(this, "준비 중인 기능입니다.", Toast.LENGTH_SHORT).show());
        }

        // TODO: GET /me API 호출하여 프로필 정보 로드
        loadDummyProfile();
    }

    private void loadDummyProfile() {
        tvNickname.setText("사용자");
        tvUsername.setText(prefs.getString("saved_id", "guest"));
        tvPhone.setText("010-0000-0000");
    }
}
