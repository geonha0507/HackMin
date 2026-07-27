package com.hackmin.app.ui.order;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.user.AccountRegisterRequest;
import com.hackmin.app.data.model.user.BankAccountDto;
import com.hackmin.app.data.model.user.PaymentCardDto;
import com.hackmin.app.network.ApiClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 마이페이지 → 결제수단 등록 허브.
 * - 현재 등록된 결제수단(카드/계좌/카카오페이/네이버페이)을 보여주고 삭제할 수 있다.
 * - 카카오/네이버 등록 클릭 시 이미 등록돼 있으면 "기존 사용 / 새로 등록"을 묻고, 없으면 바로 등록.
 * - 여기서의 등록/삭제는 서버(암호화 저장)에 반영되므로 결제화면에도 그대로 나타난다.
 */
public class PaymentMethodsActivity extends AppCompatActivity {

    private static final String[] BANKS = {
            "국민", "신한", "우리", "하나", "농협", "기업", "카카오뱅크", "토스뱅크", "SC제일", "케이뱅크"
    };

    private LinearLayout registeredContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(scroll);

        root.addView(sectionTitle("결제수단 등록", 22));

        // 현재 등록된 결제수단 목록.
        root.addView(sectionTitle("등록된 결제수단", 14));
        registeredContainer = new LinearLayout(this);
        registeredContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(registeredContainer);

        root.addView(divider());

        // 등록 진입.
        root.addView(sectionTitle("추가하기", 14));
        root.addView(menuRow("카드 등록", v -> openEasyPay("card", true)));
        root.addView(menuRow("계좌 등록", v -> showAddAccountDialog()));
        root.addView(menuRow("카카오페이 등록", v -> onEasyPayEntry("kakao")));
        root.addView(menuRow("네이버페이 등록", v -> onEasyPayEntry("naver")));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRegistered();
    }

    // ── 등록 목록 ────────────────────────────────────────────
    private void refreshRegistered() {
        registeredContainer.removeAllViews();

        ApiClient.userApi(this).getPaymentCards(null).enqueue(new Callback<PagedResponse<PaymentCardDto>>() {
            @Override
            public void onResponse(@NonNull Call<PagedResponse<PaymentCardDto>> call,
                                   @NonNull Response<PagedResponse<PaymentCardDto>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getResults() != null) {
                    for (PaymentCardDto c : response.body().getResults()) {
                        registeredContainer.addView(registeredRow(
                                providerLabel(c.getProvider()), c.getCardMasked(),
                                () -> deleteCard(c.getId())));
                    }
                }
                showEmptyHintIfNeeded();
            }

            @Override
            public void onFailure(@NonNull Call<PagedResponse<PaymentCardDto>> call, @NonNull Throwable t) {
                showEmptyHintIfNeeded();
            }
        });

        ApiClient.userApi(this).getBankAccounts().enqueue(new Callback<PagedResponse<BankAccountDto>>() {
            @Override
            public void onResponse(@NonNull Call<PagedResponse<BankAccountDto>> call,
                                   @NonNull Response<PagedResponse<BankAccountDto>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getResults() != null) {
                    for (BankAccountDto a : response.body().getResults()) {
                        registeredContainer.addView(registeredRow(
                                "계좌 " + a.getBank(), a.getAccountMasked(),
                                () -> deleteAccount(a.getId())));
                    }
                }
                showEmptyHintIfNeeded();
            }

            @Override
            public void onFailure(@NonNull Call<PagedResponse<BankAccountDto>> call, @NonNull Throwable t) {
                showEmptyHintIfNeeded();
            }
        });
    }

    private void showEmptyHintIfNeeded() {
        if (registeredContainer.getChildCount() == 0) {
            TextView tv = new TextView(this);
            tv.setText("등록된 결제수단이 없습니다.");
            tv.setTextColor(Color.parseColor("#999999"));
            tv.setTextSize(13);
            tv.setPadding(0, dp(8), 0, dp(8));
            registeredContainer.addView(tv);
        }
    }

    private View registeredRow(String label, String masked, Runnable onDelete) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(12);
        row.setPadding(0, p, 0, p);

        TextView info = new TextView(this);
        info.setText(label + "   " + masked);
        info.setTextColor(Color.parseColor("#222222"));
        info.setTextSize(15);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(info);

        TextView del = new TextView(this);
        del.setText("✕");
        del.setTextColor(Color.parseColor("#888888"));
        del.setTextSize(16);
        del.setPadding(dp(8), 0, dp(8), 0);
        del.setOnClickListener(v -> onDelete.run());
        row.addView(del);
        return row;
    }

    private void deleteCard(long id) {
        confirmDelete(() -> ApiClient.userApi(this).deletePaymentCard(id).enqueue(deleteCallback()));
    }

    private void deleteAccount(long id) {
        confirmDelete(() -> ApiClient.userApi(this).deleteBankAccount(id).enqueue(deleteCallback()));
    }

    private void confirmDelete(Runnable onConfirm) {
        new AlertDialog.Builder(this)
                .setMessage("삭제하시겠습니까?")
                .setPositiveButton("삭제", (d, w) -> onConfirm.run())
                .setNegativeButton("취소", null)
                .show();
    }

    private Callback<Void> deleteCallback() {
        return new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(PaymentMethodsActivity.this, "삭제되었습니다.", Toast.LENGTH_SHORT).show();
                    refreshRegistered();
                } else {
                    Toast.makeText(PaymentMethodsActivity.this, "삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Toast.makeText(PaymentMethodsActivity.this, "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_SHORT).show();
            }
        };
    }

    // ── 등록 진입 ────────────────────────────────────────────
    /** 카카오/네이버: 이미 등록돼 있으면 기존 사용/새로 등록을 묻고, 없으면 바로 등록 폼으로. */
    private void onEasyPayEntry(String provider) {
        ApiClient.userApi(this).getPaymentCards(provider).enqueue(new Callback<PagedResponse<PaymentCardDto>>() {
            @Override
            public void onResponse(@NonNull Call<PagedResponse<PaymentCardDto>> call,
                                   @NonNull Response<PagedResponse<PaymentCardDto>> response) {
                boolean registered = response.isSuccessful() && response.body() != null
                        && response.body().getResults() != null && !response.body().getResults().isEmpty();
                if (registered) {
                    new AlertDialog.Builder(PaymentMethodsActivity.this)
                            .setTitle(providerLabel(provider))
                            .setMessage("이미 등록된 " + providerLabel(provider) + "가 있습니다.")
                            .setPositiveButton("기존 사용", (d, w) -> openEasyPay(provider, false))
                            .setNegativeButton("새로 등록", (d, w) -> openEasyPay(provider, true))
                            .show();
                } else {
                    openEasyPay(provider, true);
                }
            }

            @Override
            public void onFailure(@NonNull Call<PagedResponse<PaymentCardDto>> call, @NonNull Throwable t) {
                // 조회 실패(서버 미배포 등) → 등록 폼으로.
                openEasyPay(provider, true);
            }
        });
    }

    private void openEasyPay(String provider, boolean forceNew) {
        Intent i = new Intent(this, EasyPayActivity.class);
        i.putExtra(EasyPayActivity.EXTRA_PROVIDER, provider);
        i.putExtra(EasyPayActivity.EXTRA_FORCE_NEW, forceNew);
        startActivity(i);
    }

    private String providerLabel(String provider) {
        if ("kakao".equals(provider)) return "카카오페이";
        if ("naver".equals(provider)) return "네이버페이";
        return "카드";
    }

    // ── 계좌 등록 다이얼로그 ──────────────────────────────────
    private void showAddAccountDialog() {
        int pad = dp(20);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, 0);

        final Spinner spBank = new Spinner(this);
        spBank.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, BANKS));
        box.addView(spBank);

        final EditText etAccount = new EditText(this);
        etAccount.setHint("계좌번호 (숫자 10~14자리)");
        etAccount.setInputType(InputType.TYPE_CLASS_NUMBER);
        etAccount.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14)});
        box.addView(etAccount);

        final EditText etPwd = new EditText(this);
        etPwd.setHint("비밀번호 앞 2자리");
        etPwd.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etPwd.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});
        box.addView(etPwd);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("계좌 등록")
                .setView(box)
                .setPositiveButton("등록", null)
                .setNegativeButton("취소", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String bank = (String) spBank.getSelectedItem();
            String account = etAccount.getText().toString().replaceAll("[^0-9]", "");
            String pwd = etPwd.getText().toString().trim();
            if (account.length() < 10 || account.length() > 14) {
                Toast.makeText(this, "계좌번호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (pwd.length() != 2) {
                Toast.makeText(this, "비밀번호 앞 2자리를 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            ApiClient.userApi(this).registerBankAccount(new AccountRegisterRequest(bank, account))
                    .enqueue(new Callback<BankAccountDto>() {
                        @Override
                        public void onResponse(@NonNull Call<BankAccountDto> call, @NonNull Response<BankAccountDto> response) {
                            if (response.isSuccessful()) {
                                Toast.makeText(PaymentMethodsActivity.this, "계좌가 등록되었습니다.", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                                refreshRegistered();
                            } else {
                                Toast.makeText(PaymentMethodsActivity.this, "계좌 등록에 실패했습니다.", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<BankAccountDto> call, @NonNull Throwable t) {
                            Toast.makeText(PaymentMethodsActivity.this, "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_SHORT).show();
                        }
                    });
        }));
        dialog.show();
    }

    // ── UI 헬퍼 ──────────────────────────────────────────────
    private TextView sectionTitle(String text, int sizeSp) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(sizeSp >= 20 ? Color.parseColor("#222222") : Color.parseColor("#888888"));
        if (sizeSp >= 20) tv.getPaint().setFakeBoldText(true);
        tv.setPadding(0, dp(sizeSp >= 20 ? 8 : 16), 0, dp(8));
        return tv;
    }

    private TextView menuRow(String text, View.OnClickListener onClick) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTextColor(Color.parseColor("#222222"));
        int p = dp(16);
        tv.setPadding(0, p, 0, p);
        tv.setClickable(true);
        tv.setFocusable(true);
        tv.setOnClickListener(onClick);
        return tv;
    }

    private View divider() {
        View v = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lp.topMargin = dp(8);
        lp.bottomMargin = dp(8);
        v.setLayoutParams(lp);
        v.setBackgroundColor(Color.parseColor("#EEEEEE"));
        return v;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
