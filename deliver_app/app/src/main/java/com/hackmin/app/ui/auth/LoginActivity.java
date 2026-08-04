package com.hackmin.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.hackmin.app.R;
import com.hackmin.app.ui.home.HomeActivity;
import com.hackmin.app.data.model.auth.LoginRequest;
import com.hackmin.app.data.model.auth.LoginResponse;
import com.hackmin.app.data.model.auth.UserDto;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.network.SessionManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends com.hackmin.app.ui.common.BaseActivity {

    private TextInputEditText etLoginId, etLoginPw;
    private Button btnLogin;
    private TextView tvGoSignup, tvGoFindPw;
    private CheckBox cbSaveId;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 네이티브 보안 가드 발동(훈련용): 루팅·Frida 흔적이 있으면 네이티브가 프로세스를
        // 직접 종료한다. Java에는 판정 분기가 없어 Java 후킹만으로는 우회되지 않는다.
        com.hackmin.app.security.SecurityGuard.init(this);

        session = SessionManager.getInstance(this);

        etLoginId = findViewById(R.id.et_login_id);
        etLoginPw = findViewById(R.id.et_login_pw);
        btnLogin = findViewById(R.id.btn_login);
        tvGoSignup = findViewById(R.id.tv_go_signup);
        tvGoFindPw = findViewById(R.id.tv_go_find_pw);
        cbSaveId = findViewById(R.id.cb_save_id);

        // 저장된 아이디 불러오기
        String savedId = session.getSavedId();
        if (!savedId.isEmpty()) {
            etLoginId.setText(savedId);
            cbSaveId.setChecked(true);
        }

        btnLogin.setOnClickListener(v -> {
            String id = etLoginId.getText().toString().trim();
            String pw = etLoginPw.getText().toString().trim();

            if (id.isEmpty() || pw.isEmpty()) {
                Toast.makeText(this, "아이디와 비밀번호를 모두 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            // 아이디 저장 로직
            if (cbSaveId.isChecked()) {
                session.setSavedId(id);
            } else {
                session.clearSavedId();
            }

            LoginRequest loginRequest = new LoginRequest(id, pw);
            ApiClient.authApi(this).login(loginRequest).enqueue(new Callback<LoginResponse>() {
                @Override
                public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        LoginResponse body = response.body();
                        session.saveTokens(body.getAccessToken(), body.getRefreshToken());
                        UserDto user = body.getUser();
                        if (user != null) {
                            session.saveUser(user.getId(), user.getUsername(), user.getNickname());
                        }
                        Toast.makeText(LoginActivity.this, "로그인 성공!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                        finish();
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

        // 비밀번호 칸에서 엔터(완료) 키를 누르면 로그인 실행.
        etLoginPw.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        etLoginPw.setOnEditorActionListener((tv, actionId, event) -> {
            boolean enterDown = event != null
                    && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN;
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || enterDown) {
                btnLogin.performClick();
                return true;
            }
            return false;
        });

        tvGoSignup.setOnClickListener(v -> startActivity(new Intent(this, SignupActivity.class)));
        tvGoFindPw.setOnClickListener(v -> startActivity(new Intent(this, ForgotPasswordActivity.class)));
    }
}