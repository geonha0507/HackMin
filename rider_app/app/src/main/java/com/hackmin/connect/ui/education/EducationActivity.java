package com.hackmin.connect.ui.education;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import com.hackmin.connect.R;
import com.hackmin.connect.network.SessionManager;
import com.hackmin.connect.ui.common.BaseActivity;
import com.hackmin.connect.ui.home.HomeActivity;

/**
 * 개인정보보호 교육(필수). 회원가입 후 첫 로그인 시 이 교육을 확인해야만 홈으로 진입할 수 있다.
 * 확인 완료는 아이디별로 저장되어, 다음 로그인부터는 건너뛴다(내정보에서 '다시보기' 가능).
 */
public class EducationActivity extends BaseActivity {

    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_education);

        session = SessionManager.getInstance(this);

        CheckBox cbAgree = findViewById(R.id.cb_edu_agree);
        Button btnDone = findViewById(R.id.btn_edu_done);

        // 재열람 모드(내정보 '다시보기')로 들어왔으면 이미 이수 상태 → 그냥 닫기 버튼처럼.
        boolean review = getIntent().getBooleanExtra("review", false);
        if (review) {
            cbAgree.setChecked(true);
            cbAgree.setVisibility(android.view.View.GONE);
            btnDone.setText("닫기");
        }

        btnDone.setOnClickListener(v -> {
            if (!review && !cbAgree.isChecked()) {
                Toast.makeText(this, "위 교육 내용을 확인했는지 체크해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (review) {
                finish();
                return;
            }
            session.setEducationDone(session.getUsername());
            Toast.makeText(this, "개인정보보호 교육 확인 완료!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        // 필수 교육이므로 뒤로가기로 건너뛸 수 없다(재열람 모드는 예외).
        if (getIntent().getBooleanExtra("review", false)) {
            super.onBackPressed();
        } else {
            Toast.makeText(this, "교육 확인 후 진행할 수 있어요.", Toast.LENGTH_SHORT).show();
        }
    }
}
