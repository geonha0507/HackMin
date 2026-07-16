package com.hackmin.app.ui.mypage;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hackmin.app.R;
import com.hackmin.app.data.model.common.MessageResponse;
import com.hackmin.app.data.model.user.ChangePasswordRequest;
import com.hackmin.app.network.ApiClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 비밀번호 변경 화면. PUT /me/password.
 */
public class ChangePasswordActivity extends AppCompatActivity {

    private EditText etCurrentPw, etNewPw, etNewPwConfirm;
    private Button btnSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        ImageButton btnBack = findViewById(R.id.btnBack);
        etCurrentPw = findViewById(R.id.etCurrentPw);
        etNewPw = findViewById(R.id.etNewPw);
        etNewPwConfirm = findViewById(R.id.etNewPwConfirm);
        btnSubmit = findViewById(R.id.btnSubmit);

        btnBack.setOnClickListener(v -> finish());
        btnSubmit.setOnClickListener(v -> submit());
    }

    private void submit() {
        String current = etCurrentPw.getText().toString().trim();
        String next = etNewPw.getText().toString().trim();
        String confirm = etNewPwConfirm.getText().toString().trim();

        if (current.isEmpty() || next.isEmpty() || confirm.isEmpty()) {
            Toast.makeText(this, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!next.equals(confirm)) {
            Toast.makeText(this, "새 비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmit.setEnabled(false);
        ChangePasswordRequest req = new ChangePasswordRequest(current, next);
        ApiClient.userApi(this).changePassword(req).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                btnSubmit.setEnabled(true);
                if (response.isSuccessful()) {
                    Toast.makeText(ChangePasswordActivity.this,
                            "비밀번호가 변경되었습니다.", Toast.LENGTH_SHORT).show();
                    finish();
                } else if (response.code() == 400) {
                    Toast.makeText(ChangePasswordActivity.this,
                            "현재 비밀번호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ChangePasswordActivity.this,
                            "변경에 실패했습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                btnSubmit.setEnabled(true);
                Toast.makeText(ChangePasswordActivity.this,
                        "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_LONG).show();
            }
        });
    }
}
