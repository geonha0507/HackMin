package com.hackmin.app.ui.auth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
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
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SignupActivity extends com.hackmin.app.ui.common.BaseActivity {

    private TextInputEditText etSignupNickname;
    private TextInputEditText etSignupId;
    private TextInputEditText etSignupPw;
    private TextInputEditText etSignupPwConfirm;
    private TextInputEditText etSignupName;
    private TextInputEditText etSignupPhone;
    private TextView tvNicknameCheckResult;
    private TextView tvIdCheckResult;
    private TextView tvPwCondition;
    private TextView tvPwConfirmResult;
    private TextView tvPhoneResult;
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
        etSignupName = findViewById(R.id.et_signup_name);
        etSignupPhone = findViewById(R.id.et_signup_phone);
        tvNicknameCheckResult = findViewById(R.id.tv_nickname_check_result);
        tvIdCheckResult = findViewById(R.id.tv_id_check_result);
        tvPwCondition = findViewById(R.id.tv_pw_condition);
        tvPwConfirmResult = findViewById(R.id.tv_pw_confirm_result);
        tvPhoneResult = findViewById(R.id.tv_phone_result);
        Button btnCheckNicknameDuplicate = findViewById(R.id.btn_check_nickname_duplicate);
        Button btnCheckDuplicate = findViewById(R.id.btn_check_duplicate);
        Button btnSignupSubmit = findViewById(R.id.btn_signup_submit);

        // 나가기 버튼 (뒤로가기)
        findViewById(R.id.tv_back).setOnClickListener(v -> finish());

        // 약관 전체동의 ↔ 개별항목 동기화
        setupAgreementCheckboxes();

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

            ApiClient.authApi(this).checkDuplicateNickname(nickname).enqueue(new Callback<DuplicateCheckResponse>() {
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
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(id).matches()) {
                showIdResult("이메일 형식으로 입력해주세요.", false);
                return;
            }

            ApiClient.authApi(this).checkDuplicateUsername(id).enqueue(new Callback<DuplicateCheckResponse>() {
                @Override
                public void onResponse(@NonNull Call<DuplicateCheckResponse> call, @NonNull Response<DuplicateCheckResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        if (response.body().isAvailable()) {
                            isDuplicateChecked = true;
                            showIdResult("사용 가능한 아이디입니다.", true);
                        } else {
                            isDuplicateChecked = false;
                            showIdResult("이미 사용 중인 아이디입니다.", false);
                        }
                    } else {
                        isDuplicateChecked = false;
                        showIdResult("중복 확인 실패 (서버 오류)", false);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<DuplicateCheckResponse> call, @NonNull Throwable t) {
                    isDuplicateChecked = true;
                    showIdResult("네트워크 연결 실패 (임시 중복 확인 통과)", true);
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
                updateIdFormat(s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 비밀번호 입력 시 조건 충족 여부를 실시간으로 안내
        etSignupPw.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePasswordCondition(s.toString());
                updatePasswordMatch();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 비밀번호 확인 입력 시 위 비밀번호와의 일치 여부를 실시간으로 안내
        etSignupPwConfirm.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePasswordMatch();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 전화번호 입력 시 010-XXXX-XXXX 형식으로 자동 정리 + 형식 검증 안내
        etSignupPhone.addTextChangedListener(new TextWatcher() {
            private boolean editing = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (editing) return;
                editing = true;
                String digits = s.toString().replaceAll("[^0-9]", "");
                if (digits.length() > 11) {
                    digits = digits.substring(0, 11);
                }
                String formatted = formatPhone(digits);
                s.replace(0, s.length(), formatted);
                etSignupPhone.setSelection(formatted.length());
                editing = false;
                updatePhoneResult(digits);
            }
        });

        // 2. 회원가입 완료 통신
        btnSignupSubmit.setOnClickListener(v -> {
            String nickname = etSignupNickname.getText() != null ? etSignupNickname.getText().toString().trim() : "";
            String id = etSignupId.getText() != null ? etSignupId.getText().toString().trim() : "";
            String pw = etSignupPw.getText() != null ? etSignupPw.getText().toString().trim() : "";
            String pwConfirm = etSignupPwConfirm.getText() != null ? etSignupPwConfirm.getText().toString().trim() : "";
            String name = etSignupName.getText() != null ? etSignupName.getText().toString().trim() : "";
            String phone = etSignupPhone.getText() != null ? etSignupPhone.getText().toString().trim() : "";

            if (nickname.isEmpty() || id.isEmpty() || pw.isEmpty() || pwConfirm.isEmpty()
                    || name.isEmpty() || phone.isEmpty()) {
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
            if (!pw.equals(pwConfirm)) {
                Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isPhoneValid(phone.replaceAll("[^0-9]", ""))) {
                Toast.makeText(this, "전화번호는 010으로 시작하는 11자리로 입력해주세요.", Toast.LENGTH_SHORT).show();
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
            // 필수 약관 동의 검증 (선택 항목은 동의 안 해도 가입 가능)
            CheckBox cbTerms = findViewById(R.id.cb_agree_terms);
            CheckBox cbPrivacy = findViewById(R.id.cb_agree_privacy);
            CheckBox cbAge = findViewById(R.id.cb_agree_age);
            if (!cbTerms.isChecked() || !cbPrivacy.isChecked() || !cbAge.isChecked()) {
                Toast.makeText(this, "필수 약관에 동의해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            // username=아이디, email=아이디, nickname=닉네임, name=실명, phone=전화번호 (주민번호는 수집하지 않음)
            SignupRequest request = new SignupRequest(id, id, pw, nickname, name, phone, true);

            ApiClient.authApi(this).signup(request).enqueue(new Callback<UserDto>() {
                @Override
                public void onResponse(@NonNull Call<UserDto> call, @NonNull Response<UserDto> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(SignupActivity.this, "회원가입 성공!", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        String msg = "회원가입 실패 (" + response.code() + ")";
                        try {
                            if (response.errorBody() != null) {
                                org.json.JSONObject json =
                                        new org.json.JSONObject(response.errorBody().string());
                                if (json.has("message")) {
                                    msg = json.getString("message");
                                }
                            }
                        } catch (Exception ignored) {}
                        Toast.makeText(SignupActivity.this, msg, Toast.LENGTH_LONG).show();
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
     * "전체 동의" 체크박스와 개별 약관 체크박스를 양방향 동기화한다.
     */
    private void setupAgreementCheckboxes() {
        CheckBox cbAll = findViewById(R.id.cb_agree_all);
        CheckBox[] subs = {
                findViewById(R.id.cb_agree_terms),
                findViewById(R.id.cb_agree_privacy),
                findViewById(R.id.cb_agree_age),
                findViewById(R.id.cb_agree_marketing),
                findViewById(R.id.cb_agree_event),
        };

        cbAll.setOnClickListener(v -> {
            boolean checked = cbAll.isChecked();
            for (CheckBox cb : subs) {
                cb.setChecked(checked);
            }
        });

        CompoundButton.OnCheckedChangeListener syncAll = (btn, isChecked) -> {
            boolean allChecked = true;
            for (CheckBox cb : subs) {
                if (!cb.isChecked()) {
                    allChecked = false;
                    break;
                }
            }
            cbAll.setChecked(allChecked);
        };
        for (CheckBox cb : subs) {
            cb.setOnCheckedChangeListener(syncAll);
        }
    }

    private void showNicknameResult(String message, boolean available) {
        tvNicknameCheckResult.setText(message);
        tvNicknameCheckResult.setTextColor(ContextCompat.getColor(this,
                available ? android.R.color.holo_green_dark : android.R.color.holo_red_dark));
        tvNicknameCheckResult.setVisibility(View.VISIBLE);
    }

    private void showIdResult(String message, boolean available) {
        tvIdCheckResult.setText(message);
        tvIdCheckResult.setTextColor(ContextCompat.getColor(this,
                available ? android.R.color.holo_green_dark : android.R.color.holo_red_dark));
        tvIdCheckResult.setVisibility(View.VISIBLE);
    }

    private void updateIdFormat(String id) {
        if (id.isEmpty()) {
            tvIdCheckResult.setVisibility(View.GONE);
            return;
        }
        if (android.util.Patterns.EMAIL_ADDRESS.matcher(id).matches()) {
            tvIdCheckResult.setText("올바른 형식입니다.");
            tvIdCheckResult.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
        } else {
            tvIdCheckResult.setText("이메일 형식으로 입력해주세요.");
            tvIdCheckResult.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
        tvIdCheckResult.setVisibility(View.VISIBLE);
    }

    private boolean isPasswordValid(String pw) {
        return pw.length() >= 8
                && pw.matches(".*[a-zA-Z].*")
                && pw.matches(".*[0-9].*")
                && pw.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*");
    }

    private void updatePasswordCondition(String pw) {
        if (pw.isEmpty()) {
            tvPwCondition.setText("8자 이상 · 영문 · 숫자 · 특수문자 포함");
            tvPwCondition.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        } else if (isPasswordValid(pw)) {
            tvPwCondition.setText("사용 가능한 비밀번호입니다.");
            tvPwCondition.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
        } else {
            tvPwCondition.setText("8자 이상 · 영문 · 숫자 · 특수문자 포함");
            tvPwCondition.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        }
    }

    private void updatePasswordMatch() {
        String pw = etSignupPw.getText().toString();
        String pwConfirm = etSignupPwConfirm.getText().toString();
        if (pwConfirm.isEmpty()) {
            tvPwConfirmResult.setVisibility(View.GONE);
            return;
        }
        tvPwConfirmResult.setVisibility(View.VISIBLE);
        if (pw.equals(pwConfirm)) {
            tvPwConfirmResult.setText("비밀번호가 일치합니다.");
            tvPwConfirmResult.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
        } else {
            tvPwConfirmResult.setText("비밀번호가 일치하지 않습니다.");
            tvPwConfirmResult.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        }
    }

    private String formatPhone(String digits) {
        if (digits.length() <= 3) {
            return digits;
        }
        if (digits.length() <= 7) {
            return digits.substring(0, 3) + "-" + digits.substring(3);
        }
        return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
    }

    private boolean isPhoneValid(String digits) {
        return digits.length() == 11 && digits.startsWith("010");
    }

    private void updatePhoneResult(String digits) {
        if (digits.isEmpty()) {
            tvPhoneResult.setVisibility(View.GONE);
            return;
        }
        tvPhoneResult.setVisibility(View.VISIBLE);
        if (isPhoneValid(digits)) {
            tvPhoneResult.setText("올바른 형식입니다.");
            tvPhoneResult.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
        } else {
            tvPhoneResult.setText("010으로 시작하는 11자리 번호를 입력해주세요.");
            tvPhoneResult.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        }
    }
}
