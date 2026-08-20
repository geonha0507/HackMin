package com.hackmin.connect.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.hackmin.connect.R;
import com.hackmin.connect.data.model.auth.LoginRequest;
import com.hackmin.connect.data.model.auth.LoginResponse;
import com.hackmin.connect.data.model.auth.UserDto;
import com.hackmin.connect.network.ApiClient;
import com.hackmin.connect.network.SessionManager;
import com.hackmin.connect.ui.common.BaseActivity;
import com.hackmin.connect.ui.home.HomeActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 라이더 로그인. 해킹의 민족과 같은 계정 체계를 쓰되,
 * role=rider 계정만 통과시킨다(고객/점주 계정은 거부).
 */
public class LoginActivity extends BaseActivity {

    private TextInputEditText etLoginId, etLoginPw;
    private Button btnLogin;
    private CheckBox cbSaveId;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        session = SessionManager.getInstance(this);

        etLoginId = findViewById(R.id.et_login_id);
        etLoginPw = findViewById(R.id.et_login_pw);
        btnLogin = findViewById(R.id.btn_login);
        cbSaveId = findViewById(R.id.cb_save_id);

        // 이미 라이더로 로그인돼 있으면 바로 홈으로.
        if (session.isLoggedIn() && "rider".equals(session.getRole())) {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
            return;
        }

        // 저장된 아이디 불러오기
        String savedId = session.getSavedId();
        if (!savedId.isEmpty()) {
            etLoginId.setText(savedId);
            cbSaveId.setChecked(true);
        }

        btnLogin.setOnClickListener(v -> doLogin());

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
    }

    private void doLogin() {
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

        ApiClient.authApi(this).login(new LoginRequest(id, pw)).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(LoginActivity.this,
                            "로그인 실패: 정보를 확인해 주세요.", Toast.LENGTH_SHORT).show();
                    return;
                }
                LoginResponse body = response.body();
                UserDto user = body.getUser();

                // 라이더 전용 앱 — 다른 역할 계정은 세션을 만들지 않고 거부한다.
                if (user == null || !"rider".equals(user.getRole())) {
                    Toast.makeText(LoginActivity.this,
                            "라이더 계정이 아닙니다. 해킹커넥트는 라이더 전용 앱이에요.",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                session.saveTokens(body.getAccessToken(), body.getRefreshToken());
                session.saveUser(user.getId(), user.getUsername(), user.getNickname(), user.getRole());
                Toast.makeText(LoginActivity.this, "로그인 성공!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                finish();
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                Toast.makeText(LoginActivity.this,
                        "네트워크 연결 실패 (서버 상태를 확인해 주세요)", Toast.LENGTH_LONG).show();
            }
        });
    }
}
