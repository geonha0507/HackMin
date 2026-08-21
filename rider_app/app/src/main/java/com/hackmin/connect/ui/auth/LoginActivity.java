package com.hackmin.connect.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.hackmin.connect.R;
import com.hackmin.connect.data.model.auth.LoginRequest;
import com.hackmin.connect.data.model.auth.LoginResponse;
import com.hackmin.connect.data.model.auth.UserDto;
import com.hackmin.connect.network.ApiClient;
import com.hackmin.connect.network.SessionManager;
import com.hackmin.connect.security.SecurityGuard;
import com.hackmin.connect.security.TxnSigner;
import com.hackmin.connect.ui.common.BaseActivity;
import com.hackmin.connect.ui.education.EducationActivity;
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
        // 루팅 기기 차단(dex 가드). 루팅이면 종료 절차를 밟고 여기서 중단한다.
        if (SecurityGuard.enforce(this)) {
            return;
        }
        setContentView(R.layout.activity_login);

        session = SessionManager.getInstance(this);

        etLoginId = findViewById(R.id.et_login_id);
        etLoginPw = findViewById(R.id.et_login_pw);
        btnLogin = findViewById(R.id.btn_login);
        cbSaveId = findViewById(R.id.cb_save_id);

        // 이미 라이더로 로그인돼 있으면 교육 이수 여부에 따라 진입.
        if (session.isLoggedIn() && "rider".equals(session.getRole())) {
            goAfterLogin();
            return;
        }

        // 저장된 아이디 불러오기
        String savedId = session.getSavedId();
        if (!savedId.isEmpty()) {
            etLoginId.setText(savedId);
            cbSaveId.setChecked(true);
        }

        btnLogin.setOnClickListener(v -> doLogin());

        // 라이더 지원(회원가입) — role=rider 로 가입한다.
        TextView tvGoSignup = findViewById(R.id.tv_go_signup);
        tvGoSignup.setOnClickListener(v ->
                startActivity(new Intent(this, SignupActivity.class)));

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

    /**
     * [방어 ④] Keystore(EC) 개인키를 최초 1회 생성하고 공개키를 서버에 등록한다.
     * 계좌변경 요청 서명 검증에 쓰인다. 실패해도 로그인엔 지장 없음(계좌변경 시 401로 드러남).
     */
    private void registerTxnKeyAsync() {
        new Thread(() -> {
            try {
                String pem = TxnSigner.ensurePublicKeyPem();
                java.util.Map<String, String> body = new java.util.HashMap<>();
                body.put("key_id", "rider-default");
                body.put("public_key_pem", pem);
                ApiClient.riderApi(LoginActivity.this).registerTxnKey(body).execute();
            } catch (Exception ignored) {
                // best-effort 등록
            }
        }).start();
    }

    /** 로그인 후 진입: 개인정보보호 교육 미이수면 교육 화면, 이수했으면 홈. */
    private void goAfterLogin() {
        boolean eduDone = session.isEducationDone(session.getUsername());
        Intent intent = new Intent(this,
                eduDone ? HomeActivity.class : EducationActivity.class);
        startActivity(intent);
        finish();
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
                registerTxnKeyAsync();
                Toast.makeText(LoginActivity.this, "로그인 성공!", Toast.LENGTH_SHORT).show();
                goAfterLogin();
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                Toast.makeText(LoginActivity.this,
                        "네트워크 연결 실패 (서버 상태를 확인해 주세요)", Toast.LENGTH_LONG).show();
            }
        });
    }
}
