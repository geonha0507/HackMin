package com.hackmin.app.ui.mypage;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.hackmin.app.R;
import com.hackmin.app.data.model.user.UserProfileDto;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.network.SessionManager;
import com.hackmin.app.ui.auth.LoginActivity;
import com.hackmin.app.ui.order.OrderHistoryActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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

        // 배송지 관리
        findViewById(R.id.menu_addresses).setOnClickListener(v ->
                startActivity(new Intent(this, AddressActivity.class)));

        // 마이페이지 하위 기능 연결
        findViewById(R.id.menu_reviews).setOnClickListener(v ->
                startActivity(new Intent(this, MyReviewsActivity.class)));
        findViewById(R.id.menu_coupons).setOnClickListener(v ->
                startActivity(new Intent(this, CouponsActivity.class)));
        findViewById(R.id.menu_membership).setOnClickListener(v ->
                startActivity(new Intent(this, MembershipActivity.class)));
        findViewById(R.id.menu_change_password).setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class)));
        findViewById(R.id.menu_withdraw).setOnClickListener(v -> confirmWithdraw());

        // ===== [C] START: 세션값으로 즉시 표시 후 GET /me로 최신 프로필 갱신 (TODO(C) 완료) =====
        loadProfileFromSession(); // 동료(SessionManager) 기반 즉시 표시
        loadProfile();            // 서버에서 최신 정보(전화번호 등) 갱신
        // ===== [C] END =====
    }

    /** 회원 탈퇴: 확인 다이얼로그 → DELETE /me → 세션 정리 후 로그인 화면 이동. */
    private void confirmWithdraw() {
        new AlertDialog.Builder(this)
                .setTitle("회원 탈퇴")
                .setMessage("정말 탈퇴하시겠어요? 탈퇴 후에는 계정을 복구할 수 없습니다.")
                .setPositiveButton("탈퇴", (d, w) -> withdraw())
                .setNegativeButton("취소", null)
                .show();
    }

    private void withdraw() {
        ApiClient.userApi(this).withdraw().enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(MyPageActivity.this, "탈퇴되었습니다.", Toast.LENGTH_SHORT).show();
                    session.clear();
                    Intent intent = new Intent(MyPageActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                } else {
                    Toast.makeText(MyPageActivity.this, "탈퇴에 실패했습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(MyPageActivity.this,
                        "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadProfileFromSession() {
        String nickname = session.getNickname();
        String username = session.getUsername();
        tvNickname.setText(nickname.isEmpty() ? "사용자" : nickname);
        tvUsername.setText(username.isEmpty() ? session.getSavedId() : username);
        tvPhone.setText("010-0000-0000");
    }

    // ===== [C] START: 마이페이지 프로필 GET /me 연동 =====
    private void loadProfile() {
        ApiClient.userApi(this).getMe().enqueue(new Callback<UserProfileDto>() {
            @Override
            public void onResponse(Call<UserProfileDto> call, Response<UserProfileDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UserProfileDto me = response.body();
                    tvNickname.setText(me.getNickname() != null ? me.getNickname() : me.getUsername());
                    tvUsername.setText(me.getUsername());
                    tvPhone.setText(me.getPhone() != null ? me.getPhone() : "-");
                }
                // 실패(비정상 응답) 시엔 loadProfileFromSession()으로 이미 표시된 값을 유지
            }

            @Override
            public void onFailure(Call<UserProfileDto> call, Throwable t) {
                // 네트워크 오류 시 세션 표시값 유지 (별도 알림 없이 무시)
            }
        });
    }
    // ===== [C] END =====
}
