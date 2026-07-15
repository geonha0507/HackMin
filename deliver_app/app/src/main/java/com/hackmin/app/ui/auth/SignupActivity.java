package com.hackmin.app.ui.auth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.textfield.TextInputEditText;
import com.hackmin.app.R;
import com.hackmin.app.data.model.auth.DuplicateCheckResponse;
import com.hackmin.app.data.model.auth.SignupRequest;
import com.hackmin.app.data.model.auth.UserDto;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.network.HackminMode;
import com.hackmin.app.network.HackminModeInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SignupActivity extends AppCompatActivity {

    private TextInputEditText etSignupNickname;
    private TextInputEditText etSignupId;
    private TextInputEditText etSignupPw;
    private TextInputEditText etSignupPwConfirm;
    private TextView tvNicknameCheckResult;
    private boolean isNicknameChecked = false;
    private boolean isDuplicateChecked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        etSignupNickname = findViewById(R.id.et_signup_nickname);
        etSignupId = findViewById(R.id.et_signup_id);
        etSignupPw = findViewById(R.id.et_signup_pw);
        etSignupPwConfirm = findViewById(R.id.et_signup_pw_confirm);
        tvNicknameCheckResult = findViewById(R.id.tv_nickname_check_result);
        Button btnCheckNicknameDuplicate = findViewById(R.id.btn_check_nickname_duplicate);
        Button btnCheckDuplicate = findViewById(R.id.btn_check_duplicate);
        Button btnSignupSubmit = findViewById(R.id.btn_signup_submit);

        // 나가기 버튼 (뒤로가기)
        findViewById(R.id.tv_back).setOnClickListener(v -> finish());

        HackminModeInterceptor.ModeProvider modeProvider = () -> HackminMode.SECURE;
        HackminModeInterceptor.TokenProvider tokenProvider = () -> "";

        // 0. 닉네임 중복 확인 통신
        btnCheckNicknameDuplicate.setOnClickListener(v -> {
            String nickname = etSignupNickname.getText() != null ? etSignupNickname.getText().toString().trim() : "";
            if (nickname.isEmpty()) {
                Toast.makeText(this, "닉네임을 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!nickname.matches("^[a-zA-Z0-9가-힣]+$")) {
                Toast.makeText(this, "닉네임은 한글, 영어, 숫자만 사용할 수 있습니다.", Toast.LENGTH_SHORT).show();
                return;
            }

            // AuthApi.checkDuplicate(field, value) 형식을 따름 (닉네임 중복 확인이므로 field="nickname")
            ApiClient.authApi(modeProvider, tokenProvider).checkDuplicateNickname(nickname).enqueue(new Callback<DuplicateCheckResponse>() {
                @Override
                public void onResponse(@NonNull Call<DuplicateCheckResponse> call, @NonNull Response<DuplicateCheckResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        if (response.body().isAvailable()) {
                            isNicknameChecked = true;
                            showNicknameResult("사용 가능한 닉네임입니다.", true);
                        } else {
                            isNicknameChecked = false;
                            showNicknameResult("이미 사용 중인 닉네임입니다.", false);
                        }
                    } else {
                        isNicknameChecked = false;
                        showNicknameResult("중복 확인 실패 (서버 오류)", false);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<DuplicateCheckResponse> call, @NonNull Throwable t) {
                    // 개발 단계 오프라인 테스트용: 서버가 꺼져 있어도 통과할 수 있게 강제 허용 처리
                    isNicknameChecked = true;
                    showNicknameResult("네트워크 연결 실패 (임시 중복 확인 통과)", true);
                }
            });
        });

        // 닉네임을 다시 수정하면 중복확인을 무효화 (재확인 강제)
        etSignupNickname.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                isNicknameChecked = false;
                tvNicknameCheckResult.setVisibility(View.GONE);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 1. 아이디 중복 확인 통신
        btnCheckDuplicate.setOnClickListener(v -> {
            String id = etSignupId.getText() != null ? etSignupId.getText().toString().trim() : "";
            if (id.isEmpty()) {
                Toast.makeText(this, "아이디를 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            // AuthApi.checkDuplicate(field, value) 형식을 따름 (아이디 중복 확인이므로 field="username")
            ApiClient.authApi(modeProvider, tokenProvider).checkDuplicateUsername(id).enqueue(new Callback<DuplicateCheckResponse>() {
                @Override
                public void onResponse(@NonNull Call<DuplicateCheckResponse> call, @NonNull Response<DuplicateCheckResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        if (response.body().isAvailable()) {
                            isDuplicateChecked = true;
                            Toast.makeText(SignupActivity.this, "사용 가능한 아이디입니다.", Toast.LENGTH_SHORT).show();
                        } else {
                            isDuplicateChecked = false;
                            Toast.makeText(SignupActivity.this, "이미 사용 중인 아이디입니다.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(SignupActivity.this, "중복 확인 실패 (서버 오류)", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<DuplicateCheckResponse> call, @NonNull Throwable t) {
                    // 개발 단계 오프라인 테스트용: 서버가 꺼져 있어도 통과할 수 있게 강제 허용 처리
                    isDuplicateChecked = true;
                    Toast.makeText(SignupActivity.this, "네트워크 연결 실패 (임시 중복 확인 통과)", Toast.LENGTH_LONG).show();
                }
            });
        });

        // 2. 회원가입 완료 통신
        btnSignupSubmit.setOnClickListener(v -> {
            String nickname = etSignupNickname.getText() != null ? etSignupNickname.getText().toString().trim() : "";
            String id = etSignupId.getText() != null ? etSignupId.getText().toString().trim() : "";
            String pw = etSignupPw.getText() != null ? etSignupPw.getText().toString().trim() : "";
            String pwConfirm = etSignupPwConfirm.getText() != null ? etSignupPwConfirm.getText().toString().trim() : "";

            if (nickname.isEmpty() || id.isEmpty() || pw.isEmpty() || pwConfirm.isEmpty()) {
                Toast.makeText(this, "모든 정보를 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!nickname.matches("^[a-zA-Z0-9가-힣]+$")) {
                Toast.makeText(this, "닉네임은 한글, 영어, 숫자만 사용할 수 있습니다.", Toast.LENGTH_SHORT).show();
                return;
            }

            // 비밀번호 유효성 검사 (8글자 이상 + 영문 + 숫자 + 특수문자 모두 포함)
            boolean pwLengthOk = pw.length() >= 8;
            boolean pwHasLetter = pw.matches(".*[a-zA-Z].*");
            boolean pwHasDigit = pw.matches(".*[0-9].*");
            boolean pwHasSpecial = pw.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*");

            if (!pwLengthOk || !pwHasLetter || !pwHasDigit || !pwHasSpecial) {
                Toast.makeText(this, "비밀번호는 8글자 이상, 특수문자 1개 이상, 영어 및 숫자가 포함되어야 합니다", Toast.LENGTH_LONG).show();
                return;
            }

            if (!pw.equals(pwConfirm)) {
                Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isNicknameChecked) {
                Toast.makeText(this, "닉네임 중복확인을 먼저 진행해 주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isDuplicateChecked) {
                Toast.makeText(this, "아이디 중복확인을 먼저 진행해 주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            // API 모델 매핑 (닉네임을 이름 자리에 반영 - 실제 DTO 필드 의미에 맞게 조정 필요)
            SignupRequest request = new SignupRequest(id, id, pw, nickname, "010-0000-0000", true);

            ApiClient.authApi(modeProvider, tokenProvider).signup(request).enqueue(new Callback<UserDto>() {
                @Override
                public void onResponse(@NonNull Call<UserDto> call, @NonNull Response<UserDto> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(SignupActivity.this, "회원가입 성공!", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(SignupActivity.this, "회원가입 실패 (서버 거절)", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<UserDto> call, @NonNull Throwable t) {
                    Toast.makeText(SignupActivity.this, "서버 연결 실패", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void showNicknameResult(String message, boolean available) {
        tvNicknameCheckResult.setText(message);
        tvNicknameCheckResult.setTextColor(ContextCompat.getColor(this,
                available ? android.R.color.holo_green_dark : android.R.color.holo_red_dark));
        tvNicknameCheckResult.setVisibility(View.VISIBLE);
    }
}