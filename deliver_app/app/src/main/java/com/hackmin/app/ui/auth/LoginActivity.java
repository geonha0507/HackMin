package com.hackmin.app.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.hackmin.app.R;
import com.hackmin.app.data.model.auth.LoginRequest;
import com.hackmin.app.data.model.auth.LoginResponse;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.network.HackminMode;
import com.hackmin.app.network.HackminModeInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etLoginId, etLoginPw;
    private Button btnLogin;
    private TextView tvGoSignup, tvGoFindPw;
    private CheckBox cbSaveId;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        prefs = getSharedPreferences("HackminPrefs", Context.MODE_PRIVATE);

        etLoginId = findViewById(R.id.et_login_id);
        etLoginPw = findViewById(R.id.et_login_pw);
        btnLogin = findViewById(R.id.btn_login);
        tvGoSignup = findViewById(R.id.tv_go_signup);
        tvGoFindPw = findViewById(R.id.tv_go_find_pw);
        cbSaveId = findViewById(R.id.cb_save_id);

        // 저장된 아이디 불러오기
        String savedId = prefs.getString("saved_id", "");
        if (!savedId.isEmpty()) {
            etLoginId.setText(savedId);
            cbSaveId.setChecked(true);
        }

        HackminModeInterceptor.ModeProvider modeProvider = () -> HackminMode.SECURE;
        HackminModeInterceptor.TokenProvider tokenProvider = () -> prefs.getString("access_token", "");

        btnLogin.setOnClickListener(v -> {
            String id = etLoginId.getText().toString().trim();
            String pw = etLoginPw.getText().toString().trim();

            if (id.isEmpty() || pw.isEmpty()) {
                Toast.makeText(this, "아이디와 비밀번호를 모두 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            // 아이디 저장 로직
            if (cbSaveId.isChecked()) {
                prefs.edit().putString("saved_id", id).apply();
            } else {
                prefs.edit().remove("saved_id").apply();
            }

            LoginRequest loginRequest = new LoginRequest(id, pw);
            ApiClient.authApi(modeProvider, tokenProvider).login(loginRequest).enqueue(new Callback<LoginResponse>() {
                @Override
                public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        String token = response.body().getAccessToken();
                        prefs.edit().putString("access_token", token).apply();
                        Toast.makeText(LoginActivity.this, "로그인 성공!", Toast.LENGTH_SHORT).show();
                        finish(); // 로그인 성공 시 현재 화면 종료
                    } else {
                        Toast.makeText(LoginActivity.this, "로그인 실패: 정보를 확인해 주세요.", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<LoginResponse> call, Throwable t) {
                    Toast.makeText(LoginActivity.this, "네트워크 연결 실패 (백엔드 서버 확인 필요)", Toast.LENGTH_LONG).show();
                }
            });
        });

        tvGoSignup.setOnClickListener(v -> startActivity(new Intent(this, SignupActivity.class)));
        tvGoFindPw.setOnClickListener(v -> startActivity(new Intent(this, ForgotPasswordActivity.class)));
    }
}