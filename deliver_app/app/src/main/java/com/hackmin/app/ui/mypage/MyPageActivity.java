package com.hackmin.app.ui.mypage;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.hackmin.app.R;
import com.hackmin.app.network.SessionManager;
import com.hackmin.app.ui.auth.LoginActivity;
import com.hackmin.app.ui.order.OrderHistoryActivity;

public class MyPageActivity extends AppCompatActivity {

    private TextView tvNickname, tvUsername, tvPhone;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mypage);

        session = SessionManager.getInstance(this);

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
            session.clear();
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

        // 로그인 시 저장해 둔 세션 정보로 우선 표시.
        // TODO(C): GET /me API로 최신 프로필(전화번호 등) 로드하여 갱신 필요.
        loadProfileFromSession();
    }

    private void loadProfileFromSession() {
        String nickname = session.getNickname();
        String username = session.getUsername();
        tvNickname.setText(nickname.isEmpty() ? "사용자" : nickname);
        tvUsername.setText(username.isEmpty() ? session.getSavedId() : username);
        tvPhone.setText("010-0000-0000");
    }
}
