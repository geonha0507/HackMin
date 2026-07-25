package com.hackmin.app.ui.mypage;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hackmin.app.R;
import com.hackmin.app.data.model.user.UpdateProfileRequest;
import com.hackmin.app.data.model.user.UserProfileDto;
import com.hackmin.app.network.ApiClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** 내 정보 수정 화면. GET /me 로 현재 값을 불러오고 PUT /me 로 저장한다. */
public class EditProfileActivity extends AppCompatActivity {

    private TextView tvUsername;
    private EditText etNickname, etName, etPhone, etEmail;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        tvUsername = findViewById(R.id.tvUsername);
        etNickname = findViewById(R.id.etNickname);
        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etEmail = findViewById(R.id.etEmail);
        btnSave = findViewById(R.id.btnSave);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> save());

        loadProfile();
    }

    private void loadProfile() {
        ApiClient.userApi(this).getMe().enqueue(new Callback<UserProfileDto>() {
            @Override
            public void onResponse(Call<UserProfileDto> call, Response<UserProfileDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UserProfileDto me = response.body();
                    tvUsername.setText("아이디: " + me.getUsername());
                    etNickname.setText(me.getNickname());
                    etName.setText(me.getName());
                    etPhone.setText(me.getPhone());
                    etEmail.setText(me.getEmail());
                } else {
                    Toast.makeText(EditProfileActivity.this,
                            "내 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<UserProfileDto> call, Throwable t) {
                Toast.makeText(EditProfileActivity.this,
                        "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void save() {
        String nickname = etNickname.getText().toString().trim();
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String email = etEmail.getText().toString().trim();

        if (TextUtils.isEmpty(nickname)) {
            Toast.makeText(this, "닉네임을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSave.setEnabled(false);
        ApiClient.userApi(this).updateMe(new UpdateProfileRequest(email, phone, nickname, name))
                .enqueue(new Callback<UserProfileDto>() {
                    @Override
                    public void onResponse(Call<UserProfileDto> call, Response<UserProfileDto> response) {
                        btnSave.setEnabled(true);
                        if (response.isSuccessful()) {
                            Toast.makeText(EditProfileActivity.this,
                                    "내 정보가 수정되었습니다.", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            Toast.makeText(EditProfileActivity.this,
                                    "수정에 실패했습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<UserProfileDto> call, Throwable t) {
                        btnSave.setEnabled(true);
                        Toast.makeText(EditProfileActivity.this,
                                "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_LONG).show();
                    }
                });
    }
}
