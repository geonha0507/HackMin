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
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.hackmin.app.R;
import com.hackmin.app.network.SessionManager;
import com.hackmin.app.ui.common.CardTileFactory;
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

    private LinearLayout accountsContainer;   // 계좌(로고 행)
    private LinearLayout cardsRow;            // 카드/간편결제(좌우 스크롤 타일)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 결제수단(민감정보) 화면이므로 스크린샷/화면녹화 방지(FLAG_SECURE).
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);

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
        accountsContainer = new LinearLayout(this);
        accountsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(accountsContainer);

        HorizontalScrollView cardScroll = new HorizontalScrollView(this);
        cardScroll.setHorizontalScrollBarEnabled(false);
        cardsRow = new LinearLayout(this);
        cardsRow.setOrientation(LinearLayout.HORIZONTAL);
        int cp = dp(4);
        cardsRow.setPadding(0, cp, 0, cp);
        cardScroll.addView(cardsRow);
        root.addView(cardScroll);

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
        accountsContainer.removeAllViews();
        cardsRow.removeAllViews();

        // 카드/간편결제 → 좌우 스크롤 타일. 카카오/네이버는 브랜드 이미지 타일, 일반 카드는 그라데이션 타일.
        ApiClient.userApi(this).getPaymentCards(null).enqueue(new Callback<PagedResponse<PaymentCardDto>>() {
            @Override
            public void onResponse(@NonNull Call<PagedResponse<PaymentCardDto>> call,
                                   @NonNull Response<PagedResponse<PaymentCardDto>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getResults() != null) {
                    for (PaymentCardDto c : response.body().getResults()) {
                        final String provider = c.getProvider();
                        final String identity = provider + ":" + c.getId();
                        boolean isDefault = identity.equals(getDefaultId());
                        Runnable onTap = isDefault ? null
                                : () -> confirmSetDefault(identity, providerLabel(provider));
                        Runnable onDelete = () -> deleteCard(c.getId());
                        int logo = payLogoRes(provider);
                        View tile = (logo != 0)
                                ? CardTileFactory.createImageTile(PaymentMethodsActivity.this, logo,
                                        c.getCardMasked(), isDefault, onTap, onDelete)
                                : CardTileFactory.create(PaymentMethodsActivity.this,
                                        providerLabel(provider), c.getCardMasked(), isDefault, onTap, onDelete);
                        cardsRow.addView(tile);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<PagedResponse<PaymentCardDto>> call, @NonNull Throwable t) {}
        });

        // 계좌 → 은행 로고 행.
        ApiClient.userApi(this).getBankAccounts().enqueue(new Callback<PagedResponse<BankAccountDto>>() {
            @Override
            public void onResponse(@NonNull Call<PagedResponse<BankAccountDto>> call,
                                   @NonNull Response<PagedResponse<BankAccountDto>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getResults() != null) {
                    for (BankAccountDto a : response.body().getResults()) {
                        accountsContainer.addView(registeredRow(
                                "account:" + a.getId(), bankLogoRes(a.getBank()),
                                "계좌 " + a.getBank(), a.getAccountMasked(),
                                () -> deleteAccount(a.getId())));
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<PagedResponse<BankAccountDto>> call, @NonNull Throwable t) {}
        });
    }

    /** 카카오/네이버 브랜드 이미지 리소스. 일반 카드는 0(그라데이션 타일 사용). */
    private int payLogoRes(String provider) {
        if ("kakao".equals(provider)) return R.drawable.logo_kakaopay;
        if ("naver".equals(provider)) return R.drawable.logo_naverpay;
        return 0;
    }

    private View registeredRow(String identity, int logoRes, String label, String masked, Runnable onDelete) {
        boolean isDefault = identity.equals(getDefaultId());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(12);
        row.setPadding(0, p, 0, p);

        // 은행/카드 로고
        if (logoRes != 0) {
            ImageView logo = new ImageView(this);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(44), dp(30));
            llp.setMarginEnd(dp(12));
            logo.setLayoutParams(llp);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setImageResource(logoRes);
            row.addView(logo);
        }

        TextView info = new TextView(this);
        info.setText(label + "   " + masked);
        info.setTextColor(Color.parseColor("#222222"));
        info.setTextSize(15);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(info);

        if (isDefault) {
            // 기본 결제수단 배지
            TextView badge = new TextView(this);
            badge.setText("기본");
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(11);
            badge.setPadding(dp(8), dp(3), dp(8), dp(3));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(Color.parseColor("#FF6F61"));
            bg.setCornerRadius(dp(10));
            badge.setBackground(bg);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            blp.setMarginEnd(dp(8));
            badge.setLayoutParams(blp);
            row.addView(badge);
        } else {
            // 기본이 아니면 탭 시 기본으로 설정할지 묻는다.
            row.setClickable(true);
            row.setOnClickListener(v -> confirmSetDefault(identity, label));
        }

        TextView del = new TextView(this);
        del.setText("✕");
        del.setTextColor(Color.parseColor("#888888"));
        del.setTextSize(16);
        del.setPadding(dp(8), 0, dp(8), 0);
        del.setOnClickListener(v -> onDelete.run());
        row.addView(del);
        return row;
    }

    /** 다른 결제수단을 탭하면 기본으로 지정할지 확인한다. */
    private void confirmSetDefault(String identity, String label) {
        new AlertDialog.Builder(this)
                .setMessage(label + "을(를) 기본 결제수단으로 하시겠습니까?")
                .setPositiveButton("기본으로", (d, w) -> {
                    setDefaultId(identity);
                    Toast.makeText(this, "기본 결제수단으로 설정되었습니다.", Toast.LENGTH_SHORT).show();
                    refreshRegistered();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // 기본 결제수단 식별자를 계정별 로컬에 저장한다(결제화면에서도 이 값을 우선 선택에 쓸 수 있다).
    private String defaultPrefKey() {
        return "default_" + SessionManager.getInstance(this).getUserId();
    }

    private String getDefaultId() {
        return getSharedPreferences("payment_default", MODE_PRIVATE).getString(defaultPrefKey(), "");
    }

    private void setDefaultId(String identity) {
        getSharedPreferences("payment_default", MODE_PRIVATE).edit()
                .putString(defaultPrefKey(), identity).apply();
    }

    /** 은행명 → 로고 리소스. 이미지가 없는 은행은 0(로고 미표시). */
    private int bankLogoRes(String bank) {
        if (bank == null) return 0;
        if (bank.contains("국민")) return R.drawable.bank_kookmin;
        if (bank.contains("기업")) return R.drawable.bank_ibk;
        if (bank.contains("농협")) return R.drawable.bank_nonghyup;
        if (bank.contains("신한")) return R.drawable.bank_shinhan;
        if (bank.contains("우리")) return R.drawable.bank_woori;
        if (bank.contains("하나")) return R.drawable.bank_hana;
        if (bank.contains("카카오")) return R.drawable.bank_kakaobank;
        if (bank.contains("토스")) return R.drawable.bank_tossbank;
        if (bank.contains("SC")) return R.drawable.bank_sc;
        if (bank.contains("케이")) return R.drawable.bank_kbank;
        return 0;
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
