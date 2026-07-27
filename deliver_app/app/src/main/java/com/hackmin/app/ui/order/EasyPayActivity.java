package com.hackmin.app.ui.order;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.user.CardRegisterRequest;
import com.hackmin.app.data.model.user.PaymentCardDto;
import com.hackmin.app.data.model.user.PaymentPasswordRequest;
import com.hackmin.app.data.model.user.PaymentPasswordResponse;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.ui.common.SecurityKeypadDialog;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 카카오페이/네이버페이 전체화면 등록 화면.
 * provider=kakao면 노란 배경, naver면 연두 배경.
 * 최초 등록: 본인확인 → 카드정보 → 6자리 결제 비밀번호 설정 → 서버에 카드(AES-256 암호화) + 비밀번호(해시) 저장.
 * 이미 등록돼 있으면 마스킹값과 삭제 버튼을 보여준다.
 */
public class EasyPayActivity extends AppCompatActivity {

    public static final String EXTRA_PROVIDER = "provider";      // "kakao" | "naver" | "card"
    public static final String EXTRA_FORCE_NEW = "force_new";    // true면 등록 폼을 강제로 보여준다

    private String provider;
    private LinearLayout root;
    private String payPwValue = "";        // 결제 비밀번호(설정)
    private String payPwConfirmValue = ""; // 결제 비밀번호(확인)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        provider = getIntent().getStringExtra(EXTRA_PROVIDER);
        if (provider == null) provider = "kakao";

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(providerColor());
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(scroll);

        if (getIntent().getBooleanExtra(EXTRA_FORCE_NEW, false)) {
            // "새로 등록" → 이미 등록돼 있어도 등록 폼을 보여준다.
            showRegisterForm();
        } else {
            // 등록 여부에 따라 화면을 구성한다(등록돼 있으면 마스킹+삭제 화면).
            loadState();
        }
    }

    private void loadState() {
        ApiClient.userApi(this).getPaymentCards(provider).enqueue(new Callback<PagedResponse<PaymentCardDto>>() {
            @Override
            public void onResponse(@NonNull Call<PagedResponse<PaymentCardDto>> call,
                                   @NonNull Response<PagedResponse<PaymentCardDto>> response) {
                PaymentCardDto existing = null;
                if (response.isSuccessful() && response.body() != null && response.body().getResults() != null
                        && !response.body().getResults().isEmpty()) {
                    existing = response.body().getResults().get(0);
                }
                if (existing != null) {
                    showRegistered(existing);
                } else {
                    showRegisterForm();
                }
            }

            @Override
            public void onFailure(@NonNull Call<PagedResponse<PaymentCardDto>> call, @NonNull Throwable t) {
                // 서버 조회 실패(미배포 등) → 등록 폼을 보여준다.
                showRegisterForm();
            }
        });
    }

    // ── 등록 폼 ────────────────────────────────────────────────
    private void showRegisterForm() {
        root.removeAllViews();
        addTitle(providerName() + " 등록");
        addLabel("본인확인이 완료되었습니다. 결제에 사용할 카드를 등록해 주세요.");

        addLabel("카드번호");
        final EditText[] segs = buildCardSegments();

        addLabel("CVC (3자리)");
        final EditText etCvc = numberField("3자리", 3, false);
        root.addView(etCvc);

        addLabel("카드 비밀번호 앞 2자리");
        final EditText etCardPw = numberField("2자리", 2, true);
        root.addView(etCardPw);

        addLabel("결제 비밀번호 설정 (6자리)");
        payPwValue = "";
        final EditText etPayPw = pinField("탭하여 6자리 입력");
        etPayPw.setOnClickListener(v -> SecurityKeypadDialog.show(this, provider, pin -> {
            payPwValue = pin;
            etPayPw.setText("●●●●●●");
        }));
        root.addView(etPayPw);

        addLabel("결제 비밀번호 확인 (6자리)");
        payPwConfirmValue = "";
        final EditText etPayPwConfirm = pinField("탭하여 한 번 더 입력");
        etPayPwConfirm.setOnClickListener(v -> SecurityKeypadDialog.show(this, provider, pin -> {
            payPwConfirmValue = pin;
            etPayPwConfirm.setText("●●●●●●");
        }));
        root.addView(etPayPwConfirm);

        Button btnDone = filledButton("등록 완료");
        btnDone.setOnClickListener(v -> {
            StringBuilder cn = new StringBuilder();
            for (EditText seg : segs) cn.append(seg.getText().toString().trim());
            String card = cn.toString();
            String cvc = etCvc.getText().toString().trim();
            String cardPw = etCardPw.getText().toString().trim();
            if (card.length() != 16) {
                toast("카드번호를 16자리로 입력해주세요.");
                return;
            }
            if (cvc.length() != 3) {
                toast("CVC를 3자리로 입력해주세요.");
                return;
            }
            if (cardPw.length() != 2) {
                toast("카드 비밀번호 앞 2자리를 입력해주세요.");
                return;
            }
            if (payPwValue.length() != 6) {
                toast("결제 비밀번호 6자리를 설정해주세요.");
                return;
            }
            if (!payPwValue.equals(payPwConfirmValue)) {
                toast("결제 비밀번호가 일치하지 않습니다.");
                return;
            }
            btnDone.setEnabled(false);
            registerCardThenPassword(card, payPwValue, btnDone);
        });
        root.addView(btnDone);
        root.addView(textButton("닫기", v -> finish()));
    }

    /** 카드 등록(암호화 저장) → 성공 시 결제 비밀번호(해시) 설정. */
    private void registerCardThenPassword(String cardNumber, String payPw, Button btnDone) {
        ApiClient.userApi(this).registerPaymentCard(new CardRegisterRequest(provider, cardNumber))
                .enqueue(new Callback<PaymentCardDto>() {
                    @Override
                    public void onResponse(@NonNull Call<PaymentCardDto> call, @NonNull Response<PaymentCardDto> response) {
                        if (!response.isSuccessful()) {
                            btnDone.setEnabled(true);
                            toast("등록에 실패했습니다.");
                            return;
                        }
                        ApiClient.userApi(EasyPayActivity.this)
                                .setPaymentPassword(new PaymentPasswordRequest(payPw))
                                .enqueue(new Callback<PaymentPasswordResponse>() {
                                    @Override
                                    public void onResponse(@NonNull Call<PaymentPasswordResponse> c, @NonNull Response<PaymentPasswordResponse> r) {
                                        toast(providerName() + " 등록이 완료되었습니다.");
                                        setResult(RESULT_OK);
                                        finish();
                                    }

                                    @Override
                                    public void onFailure(@NonNull Call<PaymentPasswordResponse> c, @NonNull Throwable t) {
                                        // 카드는 등록됐으니 완료로 처리.
                                        toast(providerName() + " 등록이 완료되었습니다.");
                                        setResult(RESULT_OK);
                                        finish();
                                    }
                                });
                    }

                    @Override
                    public void onFailure(@NonNull Call<PaymentCardDto> call, @NonNull Throwable t) {
                        btnDone.setEnabled(true);
                        toast("네트워크 오류 (서버 확인 필요)");
                    }
                });
    }

    // ── 등록됨 상태 ────────────────────────────────────────────
    private void showRegistered(PaymentCardDto card) {
        root.removeAllViews();
        addTitle(providerName());
        addLabel("등록된 결제수단");

        TextView masked = new TextView(this);
        masked.setText(card.getCardMasked());
        masked.setTextSize(20);
        masked.setTextColor(Color.parseColor("#222222"));
        masked.setPadding(0, dp(8), 0, dp(24));
        root.addView(masked);

        root.addView(textButton("삭제", v -> confirmDelete(card.getId())));
        root.addView(textButton("닫기", v -> finish()));
    }

    private void confirmDelete(long id) {
        new AlertDialog.Builder(this)
                .setMessage("카드를 삭제하시겠습니까?")
                .setPositiveButton("삭제", (d, w) ->
                        ApiClient.userApi(this).deletePaymentCard(id).enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                                toast("삭제되었습니다.");
                                setResult(RESULT_OK);
                                finish();
                            }

                            @Override
                            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                                toast("네트워크 오류 (서버 확인 필요)");
                            }
                        }))
                .setNegativeButton("취소", null)
                .show();
    }

    // ── 스타일/헬퍼 ────────────────────────────────────────────
    private int providerColor() {
        if ("naver".equals(provider)) return Color.parseColor("#C6F0C2");  // 연두 (네이버)
        if ("card".equals(provider)) return Color.parseColor("#ECEFF1");   // 연회색 (일반 카드)
        return Color.parseColor("#FEE500");                                 // 노랑 (카카오)
    }

    private String providerName() {
        if ("naver".equals(provider)) return "네이버페이";
        if ("card".equals(provider)) return "카드";
        return "카카오페이";
    }

    private void addTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(24);
        tv.setTextColor(Color.parseColor("#222222"));
        tv.getPaint().setFakeBoldText(true);
        tv.setPadding(0, dp(24), 0, dp(24));
        root.addView(tv);
    }

    private void addLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(Color.parseColor("#555555"));
        tv.setPadding(0, dp(12), 0, dp(4));
        root.addView(tv);
    }

    /** 카드번호를 4자리씩 4칸으로 나눠 입력받는다(4자리 채우면 다음 칸으로 자동 이동). root에 추가하고 배열 반환. */
    private EditText[] buildCardSegments() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(4);
        row.setLayoutParams(rlp);

        final EditText[] segs = new EditText[4];
        for (int i = 0; i < 4; i++) {
            EditText seg = new EditText(this);
            seg.setGravity(Gravity.CENTER);
            seg.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
            if (i >= 2) {
                // 뒤 8자리(3·4번째 칸)는 입력값을 *로 가린다.
                seg.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
                seg.setTransformationMethod(new StarPasswordTransformation());
            } else {
                seg.setInputType(InputType.TYPE_CLASS_NUMBER);
            }
            seg.setBackgroundColor(Color.parseColor("#FFFFFF"));
            int p = dp(10);
            seg.setPadding(p, p, p, p);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            int m = dp(4);
            lp.setMargins(m, 0, m, 0);
            seg.setLayoutParams(lp);
            segs[i] = seg;
            row.addView(seg);
        }
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            segs[i].addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (s.length() == 4 && idx < 3) {
                        segs[idx + 1].requestFocus();
                    }
                }
            });
        }
        root.addView(row);
        return segs;
    }

    /** 결제 비밀번호 표시용 필드(일반 키보드 대신 탭하면 보안 키패드가 열린다). */
    private EditText pinField(String hint) {
        EditText et = numberField(hint, 6, true);
        et.setFocusable(false);
        et.setClickable(true);
        return et;
    }

    private EditText numberField(String hint, int maxLen, boolean password) {
        EditText et = new EditText(this);
        et.setHint(hint);
        int type = InputType.TYPE_CLASS_NUMBER;
        if (password) type |= InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        et.setInputType(type);
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLen)});
        et.setBackgroundColor(Color.parseColor("#FFFFFF"));
        int p = dp(12);
        et.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        et.setLayoutParams(lp);
        return et;
    }

    private Button filledButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor("#222222"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(24);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView textButton(String text, View.OnClickListener onClick) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(Color.parseColor("#444444"));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(16), 0, dp(16));
        tv.setOnClickListener(onClick);
        return tv;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    /** 입력값을 '*'로 가려서 보여주는 변환기(카드 뒤 8자리 마스킹용). 실제 값은 그대로 유지된다. */
    private static class StarPasswordTransformation extends android.text.method.PasswordTransformationMethod {
        @Override
        public CharSequence getTransformation(CharSequence source, View view) {
            return new StarCharSequence(source);
        }

        private static class StarCharSequence implements CharSequence {
            private final CharSequence source;

            StarCharSequence(CharSequence source) {
                this.source = source;
            }

            @Override
            public int length() {
                return source.length();
            }

            @Override
            public char charAt(int index) {
                return '*';
            }

            @Override
            public CharSequence subSequence(int start, int end) {
                return new StarCharSequence(source.subSequence(start, end));
            }
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
