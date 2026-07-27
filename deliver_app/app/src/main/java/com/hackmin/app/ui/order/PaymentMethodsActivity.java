package com.hackmin.app.ui.order;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
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

import com.hackmin.app.data.model.user.AccountRegisterRequest;
import com.hackmin.app.data.model.user.BankAccountDto;
import com.hackmin.app.network.ApiClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 마이페이지 → 결제수단 등록 허브.
 * 카드/카카오페이/네이버페이 등록은 EasyPayActivity(색상 화면)로 이동하고,
 * 계좌 등록은 이 화면에서 다이얼로그로 처리한다(서버 AES-256 암호화 저장).
 */
public class PaymentMethodsActivity extends AppCompatActivity {

    private static final String[] BANKS = {
            "국민", "신한", "우리", "하나", "농협", "기업", "카카오뱅크", "토스뱅크", "SC제일", "케이뱅크"
    };

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

        TextView title = new TextView(this);
        title.setText("결제수단 등록");
        title.setTextSize(22);
        title.setTextColor(Color.parseColor("#222222"));
        title.getPaint().setFakeBoldText(true);
        title.setPadding(0, dp(8), 0, dp(20));
        root.addView(title);

        root.addView(menuRow("카드 등록", v -> openEasyPay("card")));
        root.addView(menuRow("계좌 등록", v -> showAddAccountDialog()));
        root.addView(menuRow("카카오페이 등록", v -> openEasyPay("kakao")));
        root.addView(menuRow("네이버페이 등록", v -> openEasyPay("naver")));
    }

    private void openEasyPay(String provider) {
        Intent i = new Intent(this, EasyPayActivity.class);
        i.putExtra(EasyPayActivity.EXTRA_PROVIDER, provider);
        startActivity(i);
    }

    private TextView menuRow(String text, android.view.View.OnClickListener onClick) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTextColor(Color.parseColor("#222222"));
        int p = dp(18);
        tv.setPadding(0, p, 0, p);
        tv.setClickable(true);
        tv.setFocusable(true);
        tv.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(lp);
        return tv;
    }

    /** 계좌 등록 다이얼로그(은행 + 계좌번호 10~14자리 + 비번 앞2). 서버에 암호화 저장. */
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

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
