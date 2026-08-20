package com.hackmin.connect.ui.mypage;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.hackmin.connect.R;
import com.hackmin.connect.data.model.user.UserProfileDto;
import com.hackmin.connect.network.ApiClient;
import com.hackmin.connect.network.SessionManager;
import com.hackmin.connect.ui.auth.LoginActivity;
import com.hackmin.connect.ui.common.BaseActivity;
import com.hackmin.connect.ui.common.BottomNav;
import com.hackmin.connect.ui.common.ClickGuard;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** 내정보 — 라이더 프로필 표시와 로그아웃. */
public class MyPageActivity extends BaseActivity {

    private SessionManager session;
    private TextView tvNickname, tvUsername, tvPhone, tvEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mypage);
        BottomNav.setup(this, BottomNav.Tab.MYPAGE);

        session = SessionManager.getInstance(this);

        tvNickname = findViewById(R.id.tv_nickname);
        tvUsername = findViewById(R.id.tv_username);
        tvPhone = findViewById(R.id.tv_phone);
        tvEmail = findViewById(R.id.tv_email);

        // 세션에 있는 값 먼저 표시하고, /me 응답으로 갱신한다.
        tvNickname.setText(session.getNickname().isEmpty() ? "라이더" : session.getNickname());
        tvUsername.setText(session.getUsername());

        findViewById(R.id.row_logout).setOnClickListener(v -> {
            if (!ClickGuard.allow()) return;
            confirmLogout();
        });

        loadProfile();
    }

    private void loadProfile() {
        ApiClient.userApi(this).getMe().enqueue(new Callback<UserProfileDto>() {
            @Override
            public void onResponse(Call<UserProfileDto> call, Response<UserProfileDto> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                UserProfileDto me = response.body();
                if (me.getNickname() != null && !me.getNickname().isEmpty()) {
                    tvNickname.setText(me.getNickname());
                }
                tvUsername.setText(me.getUsername() == null ? "" : me.getUsername());
                tvPhone.setText(me.getPhone() == null || me.getPhone().isEmpty() ? "-" : me.getPhone());
                tvEmail.setText(me.getEmail() == null || me.getEmail().isEmpty() ? "-" : me.getEmail());
            }

            @Override
            public void onFailure(Call<UserProfileDto> call, Throwable t) {
                // 프로필 로딩 실패는 조용히 무시(세션 값 유지).
            }
        });
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("로그아웃")
                .setMessage("로그아웃 하시겠어요?")
                .setPositiveButton("로그아웃", (dialog, which) -> doLogout())
                .setNegativeButton("취소", null)
                .show();
    }

    private void doLogout() {
        // 서버 로그아웃은 실패해도 로컬 세션은 지운다(오프라인에서도 로그아웃 가능).
        ApiClient.authApi(this).logout().enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) { /* no-op */ }

            @Override
            public void onFailure(Call<Void> call, Throwable t) { /* no-op */ }
        });
        session.clear();
        Toast.makeText(this, "로그아웃 되었어요.", Toast.LENGTH_SHORT).show();
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
