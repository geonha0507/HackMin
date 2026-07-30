package com.hackmin.app.ui.mypage;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.hackmin.app.R;
import com.hackmin.app.data.model.promotion.MembershipBenefitsDto;
import com.hackmin.app.data.model.promotion.MembershipDto;
import com.hackmin.app.data.model.promotion.MembershipSubscribeRequest;
import com.hackmin.app.network.ApiClient;

import java.text.NumberFormat;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 멤버십 조회/가입/해지 화면.
 * - GET  /me/membership
 * - GET  /membership/benefits
 * - POST /membership/subscribe
 * - POST /membership/cancel
 */
public class MembershipActivity extends com.hackmin.app.ui.common.BaseActivity {

    private TextView tvStatus, tvPrice;
    private LinearLayout containerBenefits;
    private Button btnAction;

    private boolean isActive = false;
    private final NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_membership);
        com.hackmin.app.ui.common.BottomNav.setup(this, com.hackmin.app.ui.common.BottomNav.Tab.NONE);

        ImageButton btnBack = findViewById(R.id.btnBack);
        tvStatus = findViewById(R.id.tvMembershipStatus);
        tvPrice = findViewById(R.id.tvPrice);
        containerBenefits = findViewById(R.id.containerBenefits);
        btnAction = findViewById(R.id.btnAction);

        btnBack.setOnClickListener(v -> finish());
        btnAction.setOnClickListener(v -> {
            if (isActive) {
                confirmCancel();
            } else {
                confirmSubscribe();
            }
        });

        loadStatus();
        loadBenefits();
    }

    private void loadStatus() {
        ApiClient.promotionApi(this).getMyMembership().enqueue(new Callback<MembershipDto>() {
            @Override
            public void onResponse(Call<MembershipDto> call, Response<MembershipDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    bindStatus(response.body().getStatus());
                }
            }

            @Override
            public void onFailure(Call<MembershipDto> call, Throwable t) {
                tvStatus.setText("상태를 불러오지 못했습니다.");
            }
        });
    }

    private void bindStatus(String status) {
        isActive = "active".equals(status);
        if (isActive) {
            tvStatus.setText("멤버십 가입 중 ✓");
            btnAction.setText("멤버십 해지하기");
        } else {
            tvStatus.setText("가입된 멤버십이 없습니다.");
            btnAction.setText("멤버십 가입하기");
        }
    }

    private void loadBenefits() {
        ApiClient.promotionApi(this).getMembershipBenefits()
                .enqueue(new Callback<MembershipBenefitsDto>() {
                    @Override
                    public void onResponse(Call<MembershipBenefitsDto> call,
                                           Response<MembershipBenefitsDto> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            bindBenefits(response.body());
                        }
                    }

                    @Override
                    public void onFailure(Call<MembershipBenefitsDto> call, Throwable t) {
                        // 혜택 표시는 부가정보 — 조용히 무시.
                    }
                });
    }

    private void bindBenefits(MembershipBenefitsDto data) {
        containerBenefits.removeAllViews();
        if (data.getBenefits() != null) {
            for (MembershipBenefitsDto.Benefit b : data.getBenefits()) {
                TextView tv = new TextView(this);
                tv.setText("• " + b.getTitle() + " — " + b.getDescription());
                tv.setTextSize(14);
                tv.setTextColor(0xFF495057);
                int pad = dp(6);
                tv.setPadding(0, pad, 0, pad);
                containerBenefits.addView(tv);
            }
        }
        tvPrice.setText("월 " + won.format(data.getPrice()) + "원");
    }

    /** 가입 확인 다이얼로그. 유료 멤버십이므로 클릭 즉시 가입되지 않도록 한 번 더 확인. */
    private void confirmSubscribe() {
        String priceText = tvPrice.getText() != null ? tvPrice.getText().toString().trim() : "";
        String msg = "멤버십에 가입하시겠어요?" + (priceText.isEmpty() ? "" : "\n" + priceText);
        new AlertDialog.Builder(this)
                .setTitle("멤버십 가입")
                .setMessage(msg)
                .setPositiveButton("가입", (d, w) -> subscribe())
                .setNegativeButton("취소", null)
                .show();
    }

    private void subscribe() {
        btnAction.setEnabled(false);
        ApiClient.promotionApi(this).subscribe(new MembershipSubscribeRequest("basic"))
                .enqueue(new Callback<MembershipDto>() {
                    @Override
                    public void onResponse(Call<MembershipDto> call, Response<MembershipDto> response) {
                        btnAction.setEnabled(true);
                        if (response.isSuccessful()) {
                            Toast.makeText(MembershipActivity.this,
                                    "멤버십에 가입되었습니다.", Toast.LENGTH_SHORT).show();
                            loadStatus();
                        } else {
                            Toast.makeText(MembershipActivity.this,
                                    "가입에 실패했습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<MembershipDto> call, Throwable t) {
                        btnAction.setEnabled(true);
                        Toast.makeText(MembershipActivity.this,
                                "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void confirmCancel() {
        new AlertDialog.Builder(this)
                .setTitle("멤버십 해지")
                .setMessage("멤버십을 해지하면 무료배달, 적립, 전용 쿠폰 등의 혜택이 즉시 종료됩니다. 해지하시겠어요?")
                .setPositiveButton("해지", (d, w) -> cancel())
                .setNegativeButton("취소", null)
                .show();
    }

    private void cancel() {
        btnAction.setEnabled(false);
        ApiClient.promotionApi(this).cancelMembership().enqueue(new Callback<MembershipDto>() {
            @Override
            public void onResponse(Call<MembershipDto> call, Response<MembershipDto> response) {
                btnAction.setEnabled(true);
                if (response.isSuccessful()) {
                    Toast.makeText(MembershipActivity.this,
                            "멤버십이 해지되었습니다.", Toast.LENGTH_SHORT).show();
                    loadStatus();
                } else {
                    Toast.makeText(MembershipActivity.this,
                            "해지에 실패했습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<MembershipDto> call, Throwable t) {
                btnAction.setEnabled(true);
                Toast.makeText(MembershipActivity.this,
                        "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_LONG).show();
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
