package com.hackmin.app.ui.mypage;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.hackmin.app.R;
import com.hackmin.app.data.model.auth.LoginRequest;
import com.hackmin.app.data.model.auth.LoginResponse;
import com.hackmin.app.data.model.user.UserProfileDto;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.network.SessionManager;
import com.hackmin.app.ui.common.BottomNav;
import com.hackmin.app.ui.auth.LoginActivity;
import com.hackmin.app.ui.order.OrderHistoryActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MyPageActivity extends com.hackmin.app.ui.common.BaseActivity {

    private TextView tvNickname, tvUsername, tvPhone;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 마이페이지는 개인정보 화면이므로 스크린샷/화면녹화 방지(FLAG_SECURE).
        // 최근앱 미리보기에서도 내용이 가려지고, 다른 화면에는 적용되지 않는다.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_mypage);

        session = SessionManager.getInstance(this);

        // 뒤로가기
        findViewById(R.id.tv_mypage_back).setOnClickListener(v -> finish());
        BottomNav.setup(this, BottomNav.Tab.MYPAGE);

        // 프로필 영역
        tvNickname = findViewById(R.id.tv_mypage_nickname);
        tvUsername = findViewById(R.id.tv_mypage_username);
        tvPhone = findViewById(R.id.tv_mypage_phone);

        // 메뉴 항목들
        findViewById(R.id.menu_orders).setOnClickListener(v ->
                startActivity(new Intent(this, OrderHistoryActivity.class)));

        findViewById(R.id.menu_logout).setOnClickListener(v -> confirmLogout());

        // 내 정보 수정
        View btnEditInfo = findViewById(R.id.btn_mypage_edit_info);
        if (btnEditInfo != null) {
            btnEditInfo.setOnClickListener(v ->
                    startActivity(new Intent(this, EditProfileActivity.class)));
        }

        // 배송지 관리
        findViewById(R.id.menu_addresses).setOnClickListener(v ->
                startActivity(new Intent(this, AddressActivity.class)));

        // 마이페이지 하위 기능 연결
        findViewById(R.id.menu_reviews).setOnClickListener(v ->
                startActivity(new Intent(this, MyReviewsActivity.class)));
        findViewById(R.id.menu_coupons).setOnClickListener(v ->
                startActivity(new Intent(this, CouponsActivity.class)));
        findViewById(R.id.menu_payment_methods).setOnClickListener(v ->
                startActivity(new Intent(this, com.hackmin.app.ui.order.PaymentMethodsActivity.class)));
        findViewById(R.id.menu_inquiries).setOnClickListener(v ->
                startActivity(new Intent(this, InquiryListActivity.class)));
        findViewById(R.id.menu_change_password).setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class)));
        findViewById(R.id.menu_withdraw).setOnClickListener(v -> confirmWithdraw());

        // ===== [C] START: 세션값으로 즉시 표시 후 GET /me로 최신 프로필 갱신 (TODO(C) 완료) =====
        loadProfileFromSession(); // 동료(SessionManager) 기반 즉시 표시
        loadProfile();            // 서버에서 최신 정보(전화번호 등) 갱신
        // ===== [C] END =====
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 내 정보 수정 화면에서 돌아왔을 때 최신 값 반영.
        loadProfile();
        // 하위 목록(리뷰/주문/배송지/문의/결제수단)을 미리 받아 캐시에 채운다 →
        // 각 메뉴 진입 시 네트워크 대기 없이 즉시 표시(20초 GET 캐시 활용).
        prefetchMyPageLists();
    }

    /** 마이페이지 하위 화면 데이터를 백그라운드로 미리 호출해 HTTP 캐시를 데운다. */
    private void prefetchMyPageLists() {
        try {
            ApiClient.reviewApi(this).getMyReviews(null).enqueue(MyPageActivity.noop());
            ApiClient.orderApi(this).getMyOrders(null, 1).enqueue(MyPageActivity.noop());
            ApiClient.userApi(this).getAddresses().enqueue(MyPageActivity.noop());
            ApiClient.inquiryApi(this).getInquiries().enqueue(MyPageActivity.noop());
            ApiClient.userApi(this).getPaymentCards(null).enqueue(MyPageActivity.noop());
            ApiClient.userApi(this).getBankAccounts().enqueue(MyPageActivity.noop());
        } catch (Exception ignored) {
            // 프리페치는 실패해도 무시(각 화면이 자체적으로 다시 불러옴).
        }
    }

    /** 결과를 쓰지 않는 프리페치용 콜백(응답이 HTTP 캐시에 저장되는 것만으로 충분). */
    private static <T> retrofit2.Callback<T> noop() {
        return new retrofit2.Callback<T>() {
            @Override
            public void onResponse(retrofit2.Call<T> call, retrofit2.Response<T> response) {}

            @Override
            public void onFailure(retrofit2.Call<T> call, Throwable t) {}
        };
    }

    /** 로그아웃: 확인 다이얼로그 후 세션 정리 → 로그인 화면. */
    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("로그아웃")
                .setMessage("로그아웃 하시겠습니까?")
                .setPositiveButton("로그아웃", (d, w) -> doLogout())
                .setNegativeButton("취소", null)
                .show();
    }

    private void doLogout() {
        session.clear();
        Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    /** 회원 탈퇴: 비밀번호 입력 → 검증 성공 시 DELETE /me → 세션 정리 후 로그인 화면 이동. */
    private void confirmWithdraw() {
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, 0);

        EditText etPw = new EditText(this);
        etPw.setHint("비밀번호 입력");
        etPw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(etPw);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("회원 탈퇴")
                .setMessage("정말 탈퇴하시겠어요? 탈퇴 후에는 계정을 복구할 수 없습니다.\n비밀번호를 입력해주세요.")
                .setView(box)
                .setPositiveButton("탈퇴", null)  // 비밀번호 검증 후 수동 처리(즉시 닫히지 않게).
                .setNegativeButton("취소", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String pw = etPw.getText().toString().trim();
            if (pw.isEmpty()) {
                Toast.makeText(this, "비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            verifyPasswordThenWithdraw(pw, dialog);
        }));
        dialog.show();
    }

    /** 현재 아이디+입력 비밀번호로 로그인 시도해 검증 → 성공 시 탈퇴 진행. */
    private void verifyPasswordThenWithdraw(String password, AlertDialog dialog) {
        String username = session.getUsername();
        ApiClient.authApi(this).login(new LoginRequest(username, password))
                .enqueue(new Callback<LoginResponse>() {
                    @Override
                    public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                        if (response.isSuccessful()) {
                            dialog.dismiss();
                            withdraw();
                        } else {
                            Toast.makeText(MyPageActivity.this,
                                    "비밀번호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<LoginResponse> call, Throwable t) {
                        Toast.makeText(MyPageActivity.this,
                                "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void withdraw() {
        ApiClient.userApi(this).withdraw().enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(MyPageActivity.this, "탈퇴되었습니다.", Toast.LENGTH_SHORT).show();
                    session.clear();
                    Intent intent = new Intent(MyPageActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                } else {
                    Toast.makeText(MyPageActivity.this, "탈퇴에 실패했습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(MyPageActivity.this,
                        "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadProfileFromSession() {
        String nickname = session.getNickname();
        String username = session.getUsername();
        tvNickname.setText(nickname.isEmpty() ? "사용자" : nickname);
        tvUsername.setText(username.isEmpty() ? session.getSavedId() : username);
        tvPhone.setText("010-0000-0000");
    }

    // ===== [C] START: 마이페이지 프로필 GET /me 연동 =====
    private void loadProfile() {
        ApiClient.userApi(this).getMe().enqueue(new Callback<UserProfileDto>() {
            @Override
            public void onResponse(Call<UserProfileDto> call, Response<UserProfileDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UserProfileDto me = response.body();
                    tvNickname.setText(me.getNickname() != null ? me.getNickname() : me.getUsername());
                    tvUsername.setText(me.getUsername());
                    tvPhone.setText(me.getPhone() != null ? me.getPhone() : "-");
                }
                // 실패(비정상 응답) 시엔 loadProfileFromSession()으로 이미 표시된 값을 유지
            }

            @Override
            public void onFailure(Call<UserProfileDto> call, Throwable t) {
                // 네트워크 오류 시 세션 표시값 유지 (별도 알림 없이 무시)
            }
        });
    }
    // ===== [C] END =====
}
