package com.hackmin.app.ui.auth;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.hackmin.app.R;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.network.HackminMode;
import com.hackmin.app.network.HackminModeInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.HashMap;
import java.util.Map;

public class ForgotPasswordActivity extends AppCompatActivity {

    private TextInputEditText etForgotEmail;
    private Button btnSendResetLink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        etForgotEmail = findViewById(R.id.et_forgot_email);
        btnSendResetLink = findViewById(R.id.btn_send_reset_link);
        findViewById(R.id.tv_back).setOnClickListener(v -> finish());

        HackminModeInterceptor.ModeProvider modeProvider = () -> HackminMode.SECURE;
        HackminModeInterceptor.TokenProvider tokenProvider = () -> "";

        btnSendResetLink.setOnClickListener(v -> {
            String email = etForgotEmail.getText() != null
                    ? etForgotEmail.getText().toString().trim() : "";
            if (email.isEmpty()) {
                Toast.makeText(this, "이메일 또는 아이디를 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            Map<String, String> body = new HashMap<>();
            body.put("username", email);

            ApiClient.authApi(modeProvider, tokenProvider)
                    .requestPasswordReset(body)
                    .enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                            Toast.makeText(ForgotPasswordActivity.this,
                                    "재설정 안내가 전송되었습니다.", Toast.LENGTH_SHORT).show();
                            finish();
                        }

                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {
                            Toast.makeText(ForgotPasswordActivity.this,
                                    "네트워크 연결 실패", Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }
}
