package com.hackmin.app.ui.mypage;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.hackmin.app.R;
import com.hackmin.app.data.model.auth.UserDto;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.network.HackminMode;
import com.hackmin.app.network.HackminModeInterceptor;
import com.hackmin.app.ui.auth.LoginActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MyPageActivity extends AppCompatActivity {

    private TextView tvNickname, tvUsername, tvPhone;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mypage);

        prefs = getSharedPreferences("HackminPrefs", Context.MODE_PRIVATE);

        tvNickname = findViewById(R.id.tv_mypage_nickname);
        tvUsername = findViewById(R.id.tv_mypage_username);
        tvPhone = findViewById(R.id.tv_mypage_phone);

        findViewById(R.id.tv_mypage_back).setOnClickListener(v -> finish());

        HackminModeInterceptor.ModeProvider modeProvider = () -> HackminMode.SECURE;
        HackminModeInterceptor.TokenProvider tokenProvider = () -> prefs.getString("access_token", "");

        loadMyInfo(modeProvider, tokenProvider);

        // 메뉴 클릭 리스너 (하위 화면은 추후 하나씩 구현 예정)
        findViewById(R.id.menu_addresses).setOnClickListener(v ->
                Toast.makeText(this, "배송지 관리 (준비 중)", Toast.LENGTH_SHORT).show());

        findViewById(R.id.menu_orders).setOnClickListener(v ->
                Toast.makeText(this, "주문 내역 (준비 중)", Toast.LENGTH_SHORT).show());

        findViewById(R.id.menu_reviews).setOnClickListener(v ->
                Toast.makeText(this, "작성한 리뷰 (준비 중)", Toast.LENGTH_SHORT).show());

        findViewById(R.id.menu_coupons).setOnClickListener(v ->
                Toast.makeText(this, "보유 쿠폰 (준비 중)", Toast.LENGTH_SHORT).show());

        findViewById(R.id.menu_membership).setOnClickListener(v ->
                Toast.makeText(this, "멤버십 등급 (준비 중)", Toast.LENGTH_SHORT).show());

        findViewById(R.id.menu_change_password).setOnClickListener(v ->
                Toast.makeText(this, "비밀번호 변경 (준비 중)", Toast.LENGTH_SHORT).show());

        findViewById(R.id.btn_mypage_edit_info).setOnClickListener(v ->
                Toast.makeText(this, "내 정보 수정 (준비 중)", Toast.LENGTH_SHORT).show());

        findViewById(R.id.menu_logout).setOnClickListener(v -> {
            prefs.edit().remove("access_token").apply();
            Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(MyPageActivity.this, LoginActivity.class));
            finishAffinity();
        });

        findViewById(R.id.menu_withdraw).setOnClickListener(v ->
                Toast.makeText(this, "회원 탈퇴 (준비 중)", Toast.LENGTH_SHORT).show());
    }

    private void loadMyInfo(HackminModeInterceptor.ModeProvider modeProvider,
                            HackminModeInterceptor.TokenProvider tokenProvider) {
        ApiClient.myPageApi(modeProvider, tokenProvider).getMyInfo().enqueue(new Callback<UserDto>() {
            @Override
            public void onResponse(@NonNull Call<UserDto> call, @NonNull Response<UserDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UserDto info = response.body();
                    // 서버 UserDto에 별도 nickname 필드가 없어서 name을 표시용 닉네임 자리에 사용
                    tvNickname.setText(info.getName());
                    tvUsername.setText(info.getUsername());
                    tvPhone.setText(info.getPhone());
                } else {
                    Toast.makeText(MyPageActivity.this, "내 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<UserDto> call, @NonNull Throwable t) {
                Toast.makeText(MyPageActivity.this, "서버 연결 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }
}