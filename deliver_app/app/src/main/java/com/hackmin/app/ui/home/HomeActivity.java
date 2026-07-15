package com.hackmin.app.ui.auth;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
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
    private Button btnSignupSubmit;
    private boolean isNicknameChecked = false;
    private boolean isDuplicateChecked = false;

    // 약관 동의 체크박스
    private CheckBox cbAgreeAll;
    private CheckBox cbAgreeTerms;
    private CheckBox cbAgreePrivacy;
    private CheckBox cbAgreeAge;
    private CheckBox cbAgreeMarketing;
    private CheckBox cbAgreeEvent;

    // 전체동의 체크 변경 시, 개별 항목에 다시 이벤트가 전파되며 무한루프 도는 것 방지용 플래그
    private boolean isUpdatingFromParent = false;

    private static final int COLOR_ENABLED = Color.parseColor("#FF6F61"); // coral_primary와 동일 계열
    private static final int COLOR_DISABLED = Color.parseColor("#CCCCCC"); // 회색

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
        btnSignupSubmit = findViewById(R.id.btn_signup_submit);

        cbAgreeAll = findViewById(R.id.cb_agree_all);
        cbAgreeTerms = findViewById(R.id.cb_agree_terms);
        cbAgreePrivacy = findViewById(R.id.cb_agree_privacy);
        cbAgreeAge = findViewById(R.id.cb_agree_age);
        cbAgreeMarketing = findViewById(R.id.cb_agree_marketing);
        cbAgreeEvent = findViewById(R.id.cb_agree_event);

        // 나가기 버튼 (뒤로가기)
        findViewById(R.id.tv_back).setOnClickListener(v -> finish());

        HackminModeInterceptor.ModeProvider modeProvider = () -> HackminMode.SECURE;
        HackminModeInterceptor.TokenProvider tokenProvider = () -> "";

        setupAgreementCheckboxes();
        setupFormValidationWatchers();
        updateSubmitButtonState(); // 초기 상태: 회색 비활성화

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
            ApiClient.authApi(modeProvider, tokenProvider).checkDuplicate("nickname", nickname).enqueue(new Callback<DuplicateCheckResponse>() {
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
                    updateSubmitButtonState();
                }

                @Override
                public void onFailure(@NonNull Call<DuplicateCheckResponse> call, @NonNull Throwable t) {
                    // 개발 단계 오프라인 테스트용: 서버가 꺼져 있어도 통과할 수 있게 강제 허용 처리
                    isNicknameChecked = true;
                    showNicknameResult("네트워크 연결 실패 (임시 중복 확인 통과)", true);
                    updateSubmitButtonState();
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
            public void afterTextChanged(Editable s) {
                updateSubmitButtonState();
            }
        });

        // 1. 아이디 중복 확인 통신
        btnCheckDuplicate.setOnClickListener(v -> {
            String id = etSignupId.getText() != null ? etSignupId.getText().toString().trim() : "";
            if (id.isEmpty()) {
                Toast.makeText(this, "아이디를 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            // AuthApi.checkDuplicate(field, value) 형식을 따름 (아이디 중복 확인이므로 field="username")
            ApiClient.authApi(modeProvider, tokenProvider).checkDuplicate("username", id).enqueue(new Callback<DuplicateCheckResponse>() {
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
                        isDuplicateChecked = false;
                        Toast.makeText(SignupActivity.this, "중복 확인 실패 (서버 오류)", Toast.LENGTH_SHORT).show();
                    }
                    updateSubmitButtonState();
                }

                @Override
                public void onFailure(@NonNull Call<DuplicateCheckResponse> call, @NonNull Throwable t) {
                    // 개발 단계 오프라인 테스트용: 서버가 꺼져 있어도 통과할 수 있게 강제 허용 처리
                    isDuplicateChecked = true;
                    Toast.makeText(SignupActivity.this, "네트워크 연결 실패 (임시 중복 확인 통과)", Toast.LENGTH_LONG).show();
                    updateSubmitButtonState();
                }
            });
        });

        // 아이디를 다시 수정하면 중복확인을 무효화 (재확인 강제)
        etSignupId.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                isDuplicateChecked = false;
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateSubmitButtonState();
            }
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

            if (!isPasswordValid(pw)) {
                Toast.makeText(this, "비밀번호는 8글자 이상, 특수문자 1개 이상, 영어 및 숫자가 포함되어야 합니다", Toast.LENGTH_LONG).show();
                return;
            }

            if (!java.util.Objects.equals(pw, pwConfirm)) {
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

            // 필수 약관 3개 체크 검증
            if (!cbAgreeTerms.isChecked() || !cbAgreePrivacy.isChecked() || !cbAgreeAge.isChecked()) {
                Toast.makeText(this, "필수 약관에 모두 동의해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            // API 모델 매핑 (닉네임을 이름 자리에 반영 - 실제 DTO 필드 의미에 맞게 조정 필요)
            String encryptedPw = com.hackmin.app.security.CryptoUtil.encrypt(pw);
            SignupRequest request = new SignupRequest(id, id, encryptedPw, nickname, "010-0000-0000", true);

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

    /**
     * 전체동의 체크박스와 개별 5개 항목(필수3 + 선택2) 간의 연동 로직.
     * - 전체동의 클릭 -> 5개 항목 모두 체크/해제
     * - 개별 항목 클릭 -> 5개 항목이 전부 체크되어 있으면 전체동의도 자동 체크, 하나라도 해제되면 전체동의도 해제
     */
    private void setupAgreementCheckboxes() {
        CheckBox[] children = { cbAgreeTerms, cbAgreePrivacy, cbAgreeAge, cbAgreeMarketing, cbAgreeEvent };

        cbAgreeAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingFromParent) return;
            isUpdatingFromParent = true;
            for (CheckBox child : children) {
                child.setChecked(isChecked);
            }
            isUpdatingFromParent = false;
            updateSubmitButtonState();
        });

        android.widget.CompoundButton.OnCheckedChangeListener childListener = (buttonView, isChecked) -> {
            if (isUpdatingFromParent) return;
            boolean allChecked = true;
            for (CheckBox child : children) {
                if (!child.isChecked()) {
                    allChecked = false;
                    break;
                }
            }
            isUpdatingFromParent = true;
            cbAgreeAll.setChecked(allChecked);
            isUpdatingFromParent = false;
            updateSubmitButtonState();
        };

        for (CheckBox child : children) {
            child.setOnCheckedChangeListener(childListener);
        }
    }

    /**
     * 비밀번호 / 비밀번호 확인 입력창에 TextWatcher를 달아서
     * 값이 바뀔 때마다 가입 완료 버튼 활성화 상태를 갱신.
     * (닉네임/아이디는 위쪽에서 각자 TextWatcher에 updateSubmitButtonState 호출을 추가함)
     */
    private void setupFormValidationWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateSubmitButtonState();
            }
        };
        etSignupPw.addTextChangedListener(watcher);
        etSignupPwConfirm.addTextChangedListener(watcher);
    }

    private boolean isPasswordValid(String pw) {
        boolean pwLengthOk = pw.length() >= 8;
        boolean pwHasLetter = pw.matches(".*[a-zA-Z].*");
        boolean pwHasDigit = pw.matches(".*[0-9].*");
        boolean pwHasSpecial = pw.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");
        return pwLengthOk && pwHasLetter && pwHasDigit && pwHasSpecial;
    }

    /**
     * 현재 입력값/체크박스 상태를 종합해서 가입 완료 버튼을 활성화(장미색) / 비활성화(회색) 처리.
     * 조건: 닉네임 중복확인 완료 + 아이디 중복확인 완료 + 비밀번호 형식 통과 + 비밀번호 일치 + 필수 약관 3개 모두 체크
     */
    private void updateSubmitButtonState() {
        String pw = etSignupPw.getText() != null ? etSignupPw.getText().toString().trim() : "";
        String pwConfirm = etSignupPwConfirm.getText() != null ? etSignupPwConfirm.getText().toString().trim() : "";

        boolean pwOk = isPasswordValid(pw) && pw.equals(pwConfirm);
        boolean requiredAgreementsOk = cbAgreeTerms.isChecked() && cbAgreePrivacy.isChecked() && cbAgreeAge.isChecked();

        boolean allOk = isNicknameChecked && isDuplicateChecked && pwOk && requiredAgreementsOk;

        btnSignupSubmit.setEnabled(allOk);
        int color = allOk ? COLOR_ENABLED : COLOR_DISABLED;
        btnSignupSubmit.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    private void showNicknameResult(String message, boolean available) {
        tvNicknameCheckResult.setText(message);
        tvNicknameCheckResult.setTextColor(ContextCompat.getColor(this,
                available ? android.R.color.holo_green_dark : android.R.color.holo_red_dark));
        tvNicknameCheckResult.setVisibility(View.VISIBLE);
    }
}