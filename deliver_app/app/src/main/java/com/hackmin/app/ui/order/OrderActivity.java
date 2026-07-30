package com.hackmin.app.ui.order;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.hackmin.app.R;
import com.hackmin.app.data.api.CartApi;
import com.hackmin.app.data.api.OrderApi;
import com.hackmin.app.data.api.PaymentApi;
import com.hackmin.app.data.model.cart.CartDto;
import com.hackmin.app.data.model.cart.CartItemDto;
import com.hackmin.app.data.model.cart.CartSummaryDto;
import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.order.OrderCreateRequest;
import com.hackmin.app.data.model.order.OrderDto;
import com.hackmin.app.data.model.payment.PaymentCreateRequest;
import com.hackmin.app.data.model.payment.PaymentDto;
import com.hackmin.app.data.model.user.AddressDto;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.network.SessionManager;
import com.hackmin.app.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrderActivity extends com.hackmin.app.ui.common.BaseActivity {

    /** OrderTrackingActivity 로 넘길 주문 id 인텐트 키. */
    public static final String EXTRA_ORDER_ID = "order_id";

    private TextView tvSelectedAddress, tvTotalPayment;
    private TextView tvMenuPrice, tvDeliveryFee, tvDiscount;
    private Button btnChangeAddress, btnPay;
    private EditText etRequestMessage;
    private LinearLayout containerOrderItems;

    // 결제수단 카드/간편결제 뷰 + 현재 선택값(card | kakao | naver)
    private LinearLayout cardListContainer;
    private LinearLayout accountListContainer;
    private View cardAddTile, payKakao, payNaver;
    private View selectedCardTile;
    private String selectedPayment = "card";
    private View selectedAccountRow;   // 선택된 계좌 행(결제수단=account일 때)
    private long selectedAccountId = -1;

    private CartApi cartApi;
    private OrderApi orderApi;
    private PaymentApi paymentApi;

    // 선택/입력된 배송지 (C의 저장 배송지 선택이 나오면 교체 예정)
    private String selectedAddress = "";
    private String selectedAddressDetail = "";

    private boolean submitting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_sheet);

        initApi();
        initViews();

        // 계정 구분 없이 저장돼 있던 예전 전역 캐시(배송지·카드)를 제거한다(계정 간 정보 잔존 방지).
        clearLegacyGlobalCaches();

        // 저장된 기본 배송지가 있으면 자동으로 채운다(로컬 먼저 즉시 표시).
        applySavedDefaultAddress();
        // 서버(마이페이지 배송지 관리)의 기본 배송지도 불러와 반영한다.
        loadServerDefaultAddress();

        btnChangeAddress.setOnClickListener(v -> selectAddress());
        btnPay.setOnClickListener(v -> submitOrder());

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        loadCart();
    }

    private void initApi() {
        // 토큰/모드는 SessionManager가 자동 주입 — Context만 넘기면 된다.
        cartApi = ApiClient.cartApi(this);
        orderApi = ApiClient.orderApi(this);
        paymentApi = ApiClient.paymentApi(this);
    }

    private void initViews() {
        tvSelectedAddress = findViewById(R.id.tvSelectedAddress);
        tvTotalPayment = findViewById(R.id.tvTotalPayment);
        tvMenuPrice = findViewById(R.id.tvMenuPrice);
        tvDeliveryFee = findViewById(R.id.tvDeliveryFee);
        tvDiscount = findViewById(R.id.tvDiscount);
        btnChangeAddress = findViewById(R.id.btnChangeAddress);
        btnPay = findViewById(R.id.btnPay);
        etRequestMessage = findViewById(R.id.etRequestMessage);
        setupRequestNote();
        containerOrderItems = findViewById(R.id.containerOrderItems);
        setupPaymentMethods();
    }

    /** 결제수단(카드/카카오/네이버) 선택 UI 초기화. */
    private void setupPaymentMethods() {
        cardListContainer = findViewById(R.id.cardListContainer);
        cardAddTile = findViewById(R.id.cardAdd);
        payKakao = findViewById(R.id.payKakao);
        payNaver = findViewById(R.id.payNaver);

        // 카드 추가 → 허브와 동일한 등록 화면(카드정보 + 6자리 결제비밀번호). 등록 내용은 서버에 저장돼 허브와 연동된다.
        cardAddTile.setOnClickListener(v -> {
            Intent i = new Intent(this, EasyPayActivity.class);
            i.putExtra(EasyPayActivity.EXTRA_PROVIDER, "card");
            i.putExtra(EasyPayActivity.EXTRA_FORCE_NEW, true);
            startActivity(i);
        });
        payKakao.setOnClickListener(v -> openEasyPay("kakao"));
        payNaver.setOnClickListener(v -> openEasyPay("naver"));

        // 계좌 등록 섹션: 서버(암호화 저장)에서 목록을 불러오고, 등록 버튼을 연결한다.
        accountListContainer = findViewById(R.id.accountListContainer);
        findViewById(R.id.btnAddAccount).setOnClickListener(v -> showAddAccountDialog());
        // 카드/계좌 목록 로드는 onResume에서 수행(등록/삭제 후 돌아왔을 때 최신 반영).
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 다른 화면(마이페이지 결제수단 등록·카드 등록 화면)에서 등록/삭제한 내용을 최신으로 반영한다.
        if (cardListContainer != null) {
            loadCards();
        }
        if (accountListContainer != null) {
            loadBankAccounts();
        }
    }

    // ── 계좌 등록/목록/삭제 (서버 AES-256 저장) ──────────────────────
    private static final String[] ACCOUNT_BANKS = {
            "국민", "신한", "우리", "하나", "농협", "기업", "카카오뱅크", "토스뱅크", "SC제일", "케이뱅크"
    };

    /** 서버에서 등록 계좌 목록을 불러와 화면에 표시한다. */
    private void loadBankAccounts() {
        ApiClient.userApi(this).getBankAccounts().enqueue(
                new Callback<PagedResponse<com.hackmin.app.data.model.user.BankAccountDto>>() {
            @Override
            public void onResponse(@NonNull Call<PagedResponse<com.hackmin.app.data.model.user.BankAccountDto>> call,
                                   @NonNull Response<PagedResponse<com.hackmin.app.data.model.user.BankAccountDto>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getResults() != null) {
                    accountListContainer.removeAllViews();
                    for (com.hackmin.app.data.model.user.BankAccountDto a : response.body().getResults()) {
                        buildAccountTile(a.getId(), a.getBank(), a.getAccountMasked());
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<PagedResponse<com.hackmin.app.data.model.user.BankAccountDto>> call,
                                  @NonNull Throwable t) {
                // 목록 로드 실패는 조용히 무시(등록은 여전히 가능).
            }
        });
    }

    /** 계좌 등록 다이얼로그. 은행 선택 + 계좌번호(10~14자리) + 비밀번호 앞 2자리. */
    private void showAddAccountDialog() {
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * density);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, 0);

        addFieldLabel(box, "은행", density);
        final android.widget.Spinner spBank = new android.widget.Spinner(this);
        spBank.setAdapter(new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, ACCOUNT_BANKS));
        box.addView(spBank);

        addFieldLabel(box, "계좌번호", density);
        final android.widget.EditText etAccount = new android.widget.EditText(this);
        etAccount.setHint("숫자 10~14자리");
        etAccount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etAccount.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(14)});
        box.addView(etAccount);

        addFieldLabel(box, "비밀번호 앞 2자리", density);
        final android.widget.EditText etPwd = new android.widget.EditText(this);
        etPwd.setHint("2자리");
        etPwd.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etPwd.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2)});
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
            // 비밀번호는 저장하지 않는다. 은행+계좌번호만 서버로 보내 암호화 저장.
            ApiClient.userApi(this).registerBankAccount(
                    new com.hackmin.app.data.model.user.AccountRegisterRequest(bank, account))
                    .enqueue(new Callback<com.hackmin.app.data.model.user.BankAccountDto>() {
                        @Override
                        public void onResponse(@NonNull Call<com.hackmin.app.data.model.user.BankAccountDto> call,
                                               @NonNull Response<com.hackmin.app.data.model.user.BankAccountDto> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                com.hackmin.app.data.model.user.BankAccountDto a = response.body();
                                buildAccountTile(a.getId(), a.getBank(), a.getAccountMasked());
                                Toast.makeText(OrderActivity.this, "계좌가 등록되었습니다.", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            } else {
                                Toast.makeText(OrderActivity.this, "계좌 등록에 실패했습니다.", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<com.hackmin.app.data.model.user.BankAccountDto> call,
                                              @NonNull Throwable t) {
                            Toast.makeText(OrderActivity.this, "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_SHORT).show();
                        }
                    });
        }));

        dialog.show();
    }

    /** 등록 계좌 한 줄(은행 + 마스킹 계좌번호 + 삭제 X)을 화면에 추가한다. */
    private void buildAccountTile(long id, String bank, String masked) {
        float density = getResources().getDisplayMetrics().density;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = (int) (6 * density);
        row.setLayoutParams(rlp);
        int rp = (int) (14 * density);
        row.setPadding(rp, rp, rp, rp);
        GradientDrawable rbg = new GradientDrawable();
        rbg.setColor(Color.parseColor("#F8F9FA"));
        rbg.setCornerRadius(12 * density);
        row.setBackground(rbg);
        row.setTag(rbg);   // 선택 테두리(refreshAccountBorders)에서 사용
        row.setOnClickListener(v -> selectAccount(id, row));

        // 은행 로고(있는 은행만).
        int logo = bankLogoRes(bank);
        if (logo != 0) {
            android.widget.ImageView iv = new android.widget.ImageView(this);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams((int) (44 * density), (int) (30 * density));
            llp.setMarginEnd((int) (12 * density));
            iv.setLayoutParams(llp);
            iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            iv.setImageResource(logo);
            row.addView(iv);
        }

        TextView info = new TextView(this);
        info.setText(bank + "  " + masked);
        info.setTextColor(getColor(R.color.text_primary));
        info.setTextSize(14);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(info);

        TextView btnDelete = new TextView(this);
        btnDelete.setText("✕");
        btnDelete.setTextColor(getColor(R.color.text_secondary));
        btnDelete.setTextSize(16);
        int xp = (int) (4 * density);
        btnDelete.setPadding(xp, 0, xp, 0);
        btnDelete.setOnClickListener(v -> confirmDeleteAccount(id, row));
        row.addView(btnDelete);

        accountListContainer.addView(row);
    }

    /** 은행명 → 로고 리소스. 이미지가 없는 은행은 0. */
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

    /** 계좌 삭제 확인 다이얼로그 → 서버에서 삭제. */
    private void confirmDeleteAccount(long id, View row) {
        new AlertDialog.Builder(this)
                .setMessage("계좌를 삭제하시겠습니까?")
                .setPositiveButton("삭제", (d, w) ->
                        ApiClient.userApi(this).deleteBankAccount(id).enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                                if (response.isSuccessful()) {
                                    accountListContainer.removeView(row);
                                } else {
                                    Toast.makeText(OrderActivity.this, "삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                                Toast.makeText(OrderActivity.this, "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_SHORT).show();
                            }
                        }))
                .setNegativeButton("취소", null)
                .show();
    }

    /** 간편결제(카카오/네이버) 선택 처리 + 안내 토스트. */
    private void selectPayment(String type) {
        selectedPayment = type;
        selectedCardTile = null;
        refreshCardBorders();
        payKakao.setSelected("kakao".equals(type));
        payNaver.setSelected("naver".equals(type));
        clearAccountSelection();
    }

    /** 계좌를 결제수단으로 선택(카드·간편결제 해제). */
    private void selectAccount(long id, View row) {
        selectedPayment = "account";
        selectedAccountId = id;
        selectedAccountRow = row;
        selectedCardTile = null;
        refreshCardBorders();
        payKakao.setSelected(false);
        payNaver.setSelected(false);
        refreshAccountBorders();
    }

    private void clearAccountSelection() {
        selectedAccountRow = null;
        selectedAccountId = -1;
        refreshAccountBorders();
    }

    /** 선택된 계좌 행만 테두리를 강조한다. */
    private void refreshAccountBorders() {
        if (accountListContainer == null) return;
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < accountListContainer.getChildCount(); i++) {
            View child = accountListContainer.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof GradientDrawable) {
                GradientDrawable gd = (GradientDrawable) tag;
                if (child == selectedAccountRow) {
                    gd.setStroke((int) (2 * density), Color.parseColor("#FF6F61"));
                } else {
                    gd.setStroke(0, Color.TRANSPARENT);
                }
            }
        }
    }

    /** 카카오/네이버 버튼 → 해당 간편결제를 선택하고 전체화면 등록/관리 화면을 연다. */
    private void openEasyPay(String provider) {
        selectPayment(provider);
        Intent intent = new Intent(this, EasyPayActivity.class);
        intent.putExtra(EasyPayActivity.EXTRA_PROVIDER, provider);
        startActivity(intent);
    }

    /** 결제 비밀번호 6자리를 보안 키패드(랜덤 배치)로 입력받아 서버 검증. 성공 시 onSuccess 실행. */
    private void promptPaymentPassword(Runnable onSuccess) {
        // 계좌는 별도 브랜드색이 없으니 카드(코랄) 키패드 색을 쓴다.
        String keypadTheme = "account".equals(selectedPayment) ? "card" : selectedPayment;
        com.hackmin.app.ui.common.SecurityKeypadDialog.show(this, keypadTheme, pin ->
                ApiClient.userApi(this).verifyPaymentPassword(
                        new com.hackmin.app.data.model.user.PaymentPasswordRequest(pin))
                        .enqueue(new Callback<com.hackmin.app.data.model.user.PaymentPasswordResponse>() {
                            @Override
                            public void onResponse(@NonNull Call<com.hackmin.app.data.model.user.PaymentPasswordResponse> call,
                                                   @NonNull Response<com.hackmin.app.data.model.user.PaymentPasswordResponse> response) {
                                if (response.isSuccessful() && response.body() != null && response.body().isValid()) {
                                    onSuccess.run();
                                } else {
                                    Toast.makeText(OrderActivity.this, "결제 비밀번호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<com.hackmin.app.data.model.user.PaymentPasswordResponse> call,
                                                  @NonNull Throwable t) {
                                Toast.makeText(OrderActivity.this, "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_SHORT).show();
                            }
                        }));
    }

    /** 카드 타일 선택 처리(간편결제 선택 해제). */
    private void selectCard(View card) {
        selectedPayment = "card";
        selectedCardTile = card;
        payKakao.setSelected(false);
        payNaver.setSelected(false);
        refreshCardBorders();
        clearAccountSelection();
    }

    /** 카드번호를 서버에 암호화 저장하고, 성공하면 마스킹 타일을 추가한다. */
    private void registerCardToServer(String cardNo, AlertDialog dialog) {
        ApiClient.userApi(this).registerPaymentCard(
                new com.hackmin.app.data.model.user.CardRegisterRequest("card", cardNo))
                .enqueue(new Callback<com.hackmin.app.data.model.user.PaymentCardDto>() {
                    @Override
                    public void onResponse(@NonNull Call<com.hackmin.app.data.model.user.PaymentCardDto> call,
                                           @NonNull Response<com.hackmin.app.data.model.user.PaymentCardDto> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            int[] colors = randomGradientColors();
                            buildCardTile(new CardData(response.body().getId(),
                                    response.body().getCardMasked(), colors[0], colors[1]), true);
                            Toast.makeText(OrderActivity.this, "카드가 등록되었습니다.", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        } else {
                            Toast.makeText(OrderActivity.this, "카드 등록에 실패했습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<com.hackmin.app.data.model.user.PaymentCardDto> call,
                                          @NonNull Throwable t) {
                        Toast.makeText(OrderActivity.this, "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** CardData로 카드 타일을 생성해 화면에 추가한다(cardAdd 앞에 삽입). */
    private void buildCardTile(CardData data, boolean select) {
        float density = getResources().getDisplayMetrics().density;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) (260 * density), (int) (150 * density));
        lp.setMarginEnd((int) (12 * density));
        card.setLayoutParams(lp);
        int p = (int) (20 * density);
        card.setPadding(p, p, p, p);

        // 저장된 색상으로 그라데이션 배경 구성. 테두리는 refreshCardBorders에서 stroke로 처리.
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{data.color1, data.color2});
        bg.setCornerRadius(16 * density);
        card.setBackground(bg);
        data.drawable = bg;
        card.setTag(data);

        // 상단: "MY CARD" + 우측 X(삭제) 버튼
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("MY CARD");
        title.setTextColor(Color.WHITE);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setTextSize(14);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnDelete = new TextView(this);
        btnDelete.setText("✕");
        btnDelete.setTextColor(Color.WHITE);
        btnDelete.setTextSize(16);
        int xp = (int) (4 * density);
        btnDelete.setPadding(xp, 0, xp, xp);

        top.addView(title);
        top.addView(btnDelete);
        card.addView(top);

        // 가운데 여백
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        card.addView(spacer);

        // 카드번호(마스킹) + 카드명
        TextView num = new TextView(this);
        num.setText(data.masked != null ? data.masked : maskCardNumber(data.number));
        num.setTextColor(Color.WHITE);
        num.setTextSize(16);
        card.addView(num);

        TextView name = new TextView(this);
        name.setText("루키즈카드");
        name.setTextColor(Color.parseColor("#F0F0F0"));
        name.setTextSize(12);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = (int) (4 * density);
        name.setLayoutParams(nameLp);
        card.addView(name);

        card.setOnClickListener(v -> selectCard(card));
        btnDelete.setOnClickListener(v -> confirmDeleteCard(card));

        // cardAdd 타일 바로 앞에 삽입.
        int addIndex = cardListContainer.indexOfChild(cardAddTile);
        cardListContainer.addView(card, addIndex);

        if (select) {
            selectCard(card);
        } else {
            refreshCardBorders();
        }
    }

    /** 카드 삭제 확인 다이얼로그. */
    private void confirmDeleteCard(View card) {
        new AlertDialog.Builder(this)
                .setMessage("카드를 삭제하시겠습니까?")
                .setPositiveButton("삭제", (d, w) -> {
                    Object tag = card.getTag();
                    long id = (tag instanceof CardData) ? ((CardData) tag).serverId : 0L;
                    Runnable removeTile = () -> {
                        boolean wasSelected = (card == selectedCardTile);
                        cardListContainer.removeView(card);
                        if (wasSelected) {
                            selectedCardTile = null;
                            selectFirstCardOrNone();
                        }
                    };
                    if (id > 0) {
                        ApiClient.userApi(this).deletePaymentCard(id).enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                                if (response.isSuccessful()) {
                                    removeTile.run();
                                } else {
                                    Toast.makeText(OrderActivity.this, "삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                                Toast.makeText(OrderActivity.this, "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        removeTile.run();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /** 남아있는 첫 카드를 선택. 없으면 선택 해제. */
    private void selectFirstCardOrNone() {
        for (int i = 0; i < cardListContainer.getChildCount(); i++) {
            View child = cardListContainer.getChildAt(i);
            if (child != cardAddTile) {
                selectCard(child);
                return;
            }
        }
        selectedCardTile = null;
        refreshCardBorders();
    }

    /** 카드 타일들의 선택 테두리를 갱신한다(선택된 카드만 진한 테두리). */
    private void refreshCardBorders() {
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < cardListContainer.getChildCount(); i++) {
            View child = cardListContainer.getChildAt(i);
            if (child == cardAddTile) continue;
            Object tag = child.getTag();
            if (tag instanceof CardData && ((CardData) tag).drawable != null) {
                GradientDrawable gd = ((CardData) tag).drawable;
                if (child == selectedCardTile) {
                    gd.setStroke((int) (3 * density), Color.parseColor("#1A1A1A"));
                } else {
                    gd.setStroke(0, Color.TRANSPARENT);
                }
            }
        }
    }

    /** 금액을 천 단위 콤마 + "원" 형식으로 변환한다. (예: 108000 → "108,000원") */
    private String won(int amount) {
        return String.format(java.util.Locale.KOREA, "%,d원", amount);
    }

    /** 카드번호를 "****  ****  ****  1234" 형태로 마스킹(뒤 4자리만 노출). */
    private String maskCardNumber(String cardNo) {
        String last4 = cardNo.length() >= 4 ? cardNo.substring(cardNo.length() - 4) : cardNo;
        return "****  ****  ****  " + last4;
    }

    /** 카드 배경용 랜덤 그라데이션 색상 2개 생성. */
    private int[] randomGradientColors() {
        java.util.Random r = new java.util.Random();
        float hue = r.nextInt(360);
        int c1 = Color.HSVToColor(new float[]{hue, 0.55f, 0.85f});
        int c2 = Color.HSVToColor(new float[]{(hue + 40f) % 360f, 0.7f, 0.6f});
        return new int[]{c1, c2};
    }

    // 등록 카드는 SharedPreferences에 JSON으로 저장 → 화면 재진입/앱 재시작 후에도 유지.
    // 계정별로 파일을 분리(prefs 이름에 user_id 부착)해 다른 계정의 카드가 섞이지 않게 한다.
    private static final String PREF_CARDS = "payment_cards";
    private static final String KEY_CARDS = "cards_json";

    /** 현재 로그인 계정 전용 카드 저장소. */
    private android.content.SharedPreferences cardPrefs() {
        long uid = SessionManager.getInstance(this).getUserId();
        return getSharedPreferences(PREF_CARDS + "_" + uid, MODE_PRIVATE);
    }

    /** 서버에서 등록된 카드(provider=card)를 불러와 화면에 그린다. 마이페이지·다른 곳에서 등록/삭제한 것도 그대로 반영된다. */
    private void loadCards() {
        ApiClient.userApi(this).getPaymentCards("card").enqueue(
                new Callback<PagedResponse<com.hackmin.app.data.model.user.PaymentCardDto>>() {
            @Override
            public void onResponse(@NonNull Call<PagedResponse<com.hackmin.app.data.model.user.PaymentCardDto>> call,
                                   @NonNull Response<PagedResponse<com.hackmin.app.data.model.user.PaymentCardDto>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().getResults() == null) {
                    return;
                }
                // 기존 카드 타일 제거 후 서버 목록으로 다시 그린다(카드 추가 타일은 유지).
                for (int i = cardListContainer.getChildCount() - 1; i >= 0; i--) {
                    View child = cardListContainer.getChildAt(i);
                    if (child != cardAddTile) {
                        cardListContainer.removeView(child);
                    }
                }
                boolean first = true;
                for (com.hackmin.app.data.model.user.PaymentCardDto c : response.body().getResults()) {
                    int[] colors = randomGradientColors();
                    buildCardTile(new CardData(c.getId(), c.getCardMasked(), colors[0], colors[1]), first);
                    first = false;
                }
            }

            @Override
            public void onFailure(@NonNull Call<PagedResponse<com.hackmin.app.data.model.user.PaymentCardDto>> call,
                                  @NonNull Throwable t) {
                // 서버 미배포/네트워크 실패 → 카드 없음 상태로 둔다.
            }
        });
    }

    /** 현재 화면의 카드 타일들을 JSON으로 직렬화해 저장한다. */
    private void persistCards() {
        List<CardData> list = new ArrayList<>();
        for (int i = 0; i < cardListContainer.getChildCount(); i++) {
            View child = cardListContainer.getChildAt(i);
            if (child == cardAddTile) continue;
            Object tag = child.getTag();
            if (tag instanceof CardData) {
                list.add((CardData) tag);
            }
        }
        cardPrefs().edit()
                .putString(KEY_CARDS, new com.google.gson.Gson().toJson(list))
                .apply();
    }

    /** 등록 카드 1장의 저장 데이터. drawable은 런타임 참조라 저장 대상에서 제외(transient). */
    private static class CardData {
        long serverId;      // 서버 카드 id(삭제에 사용). 0이면 서버와 연동 안 된 임시 타일.
        String number;      // 로컬 임시용(서버 카드는 전체번호를 모름)
        String masked;      // 서버가 준 마스킹값(있으면 표시에 사용)
        int color1;
        int color2;
        transient GradientDrawable drawable;

        CardData(String number, int color1, int color2) {
            this.number = number;
            this.color1 = color1;
            this.color2 = color2;
        }

        CardData(long serverId, String masked, int color1, int color2) {
            this.serverId = serverId;
            this.masked = masked;
            this.color1 = color1;
            this.color2 = color2;
        }
    }

    /**
     * 카드 추가 다이얼로그. 카드번호 16자리 + CVC 3자리 + 비밀번호 앞 2자리를 입력받는다.
     * 실제 결제사 연동은 없고(임의번호), 형식이 맞으면 바로 등록 처리한다.
     */
    private void showAddCardDialog() {
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * density);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, 0);

        // 카드번호: 4자리씩 4칸. 뒤 8자리(3·4번째 칸)는 *로 마스킹.
        addFieldLabel(box, "카드번호", density);
        LinearLayout cardRow = new LinearLayout(this);
        cardRow.setOrientation(LinearLayout.HORIZONTAL);
        final EditText[] segs = new EditText[4];
        for (int i = 0; i < 4; i++) {
            EditText seg = new EditText(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            int m = (int) (4 * density);
            lp.setMargins(m, 0, m, 0);
            seg.setLayoutParams(lp);
            seg.setGravity(android.view.Gravity.CENTER);
            seg.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            seg.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
            if (i >= 2) {
                // 뒤 8자리는 입력값을 * 로 표시.
                seg.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                        | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
                seg.setTransformationMethod(new StarPasswordTransformation());
            }
            segs[i] = seg;
            cardRow.addView(seg);
        }
        // 4자리 채우면 다음 칸으로, 빈 칸에서 지우면 이전 칸으로 포커스 이동.
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            segs[i].addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    if (s.length() == 4 && idx < 3) {
                        segs[idx + 1].requestFocus();
                    }
                }
            });
            segs[i].setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == android.view.KeyEvent.KEYCODE_DEL
                        && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                        && segs[idx].getText().length() == 0 && idx > 0) {
                    segs[idx - 1].requestFocus();
                    segs[idx - 1].setSelection(segs[idx - 1].getText().length());
                }
                return false;
            });
        }
        box.addView(cardRow);

        // CVC
        addFieldLabel(box, "CVC", density);
        final EditText etCvc = new EditText(this);
        etCvc.setHint("3자리");
        etCvc.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etCvc.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(3)});
        box.addView(etCvc);

        // 비밀번호 앞 2자리
        addFieldLabel(box, "비밀번호 앞 2자리", density);
        final EditText etPwd = new EditText(this);
        etPwd.setHint("2자리");
        etPwd.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etPwd.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2)});
        box.addView(etPwd);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("카드 추가")
                .setView(box)
                .setPositiveButton("등록", null)  // 검증 후 수동 dismiss 위해 리스너는 아래에서 설정.
                .setNegativeButton("취소", null)
                .create();

        // 형식이 틀리면 다이얼로그가 닫히지 않도록 setOnShowListener에서 버튼을 직접 처리.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            StringBuilder cn = new StringBuilder();
            for (EditText seg : segs) cn.append(seg.getText().toString().trim());
            String cardNo = cn.toString();
            String cvc = etCvc.getText().toString().trim();
            String pwd = etPwd.getText().toString().trim();

            if (cardNo.length() != 16) {
                Toast.makeText(this, "카드번호를 16자리로 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (cvc.length() != 3) {
                Toast.makeText(this, "CVC를 3자리로 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (pwd.length() != 2) {
                Toast.makeText(this, "비밀번호 앞 2자리를 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            // 서버에 카드번호를 AES-256 암호화 저장(성공 시 마스킹 타일 추가).
            registerCardToServer(cardNo, dialog);
        }));

        dialog.show();
    }

    /** 카드 추가 다이얼로그의 입력 항목 라벨을 추가한다. */
    private void addFieldLabel(LinearLayout parent, String text, float density) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(13);
        label.setPadding(0, (int) (12 * density), 0, (int) (4 * density));
        parent.addView(label);
    }

    /** 입력값을 '*' 문자로 마스킹하는 변환기(기본 ● 대신 별표 사용). */
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
            public char charAt(int index) {
                return '*';
            }

            @Override
            public int length() {
                return source.length();
            }

            @Override
            public CharSequence subSequence(int start, int end) {
                return source.subSequence(start, end);
            }
        }
    }

    /** 주문 내역·금액은 서버 장바구니에서 가져온다. (주문 생성도 서버 장바구니 기준) */
    private void loadCart() {
        cartApi.getCart().enqueue(new Callback<CartDto>() {
            @Override
            public void onResponse(@NonNull Call<CartDto> call, @NonNull Response<CartDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    renderOrderItems(response.body());
                    loadSummary();
                } else {
                    Toast.makeText(OrderActivity.this, "장바구니를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<CartDto> call, @NonNull Throwable t) {
                Toast.makeText(OrderActivity.this, "네트워크 연결 실패 (서버 확인 필요)", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderOrderItems(CartDto cart) {
        containerOrderItems.removeAllViews();
        if (cart.getItems() == null) return;
        for (CartItemDto item : cart.getItems()) {
            View row = getLayoutInflater()
                    .inflate(R.layout.item_order_line, containerOrderItems, false);
            ImageView iv = row.findViewById(R.id.ivLineThumb);
            TextView name = row.findViewById(R.id.tvLineName);
            TextView price = row.findViewById(R.id.tvLinePrice);

            ImageLoader.load(iv, item.getMenuImage());
            name.setText(item.getMenuName() + " x" + item.getQuantity());
            price.setText(won(item.getLineTotal()));

            containerOrderItems.addView(row);
        }
    }

    private void loadSummary() {
        cartApi.getCartSummary().enqueue(new Callback<CartSummaryDto>() {
            @Override
            public void onResponse(@NonNull Call<CartSummaryDto> call, @NonNull Response<CartSummaryDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    CartSummaryDto s = response.body();
                    tvMenuPrice.setText(won(s.getSubtotal()));
                    tvDeliveryFee.setText(won(s.getDeliveryFee()));
                    // 할인 금액이 있으면 -N원 형태로 표시.
                    tvDiscount.setText(s.getDiscount() > 0 ? "-" + won(s.getDiscount()) : won(0));
                    tvTotalPayment.setText(won(s.getTotal()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<CartSummaryDto> call, @NonNull Throwable t) {
                // 총액만 실패 — 목록은 표시됨. 조용히 무시.
            }
        });
    }

    /**
     * 저장된 배송지(GET /me/addresses)를 불러와 선택하게 한다.
     * 저장된 배송지가 없으면 직접 입력 다이얼로그로 폴백한다.
     */
    private void selectAddress() {
        ApiClient.userApi(this).getAddresses().enqueue(new Callback<PagedResponse<AddressDto>>() {
            @Override
            public void onResponse(@NonNull Call<PagedResponse<AddressDto>> call,
                                   @NonNull Response<PagedResponse<AddressDto>> response) {
                List<AddressDto> list = response.isSuccessful() && response.body() != null
                        ? response.body().getResults() : null;
                if (list == null || list.isEmpty()) {
                    // 저장된 배송지가 없으면 직접 입력.
                    showAddressInputDialog();
                    return;
                }
                showAddressPicker(list);
            }

            @Override
            public void onFailure(@NonNull Call<PagedResponse<AddressDto>> call, @NonNull Throwable t) {
                // 목록 조회 실패 시에도 최소한 직접 입력은 가능하게.
                showAddressInputDialog();
            }
        });
    }

    /** 저장 배송지 선택 다이얼로그(+ 직접 입력 항목). */
    private void showAddressPicker(List<AddressDto> list) {
        List<String> labels = new ArrayList<>();
        for (AddressDto a : list) {
            String label = (a.getLabel() != null && !a.getLabel().isEmpty() ? "[" + a.getLabel() + "] " : "")
                    + a.getAddress()
                    + (a.getDetail() != null && !a.getDetail().isEmpty() ? " " + a.getDetail() : "");
            labels.add(label);
        }
        labels.add("+ 직접 입력");
        String[] items = labels.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("배송지 선택")
                .setItems(items, (dialog, which) -> {
                    if (which == list.size()) {
                        showAddressInputDialog();
                        return;
                    }
                    AddressDto a = list.get(which);
                    selectedAddress = a.getAddress() != null ? a.getAddress() : "";
                    selectedAddressDetail = a.getDetail() != null ? a.getDetail() : "";
                    tvSelectedAddress.setText(
                            selectedAddress
                                    + (TextUtils.isEmpty(selectedAddressDetail) ? "" : " " + selectedAddressDetail));
                })
                .show();
    }

    /** 배송지 직접 입력 다이얼로그(저장 배송지가 없을 때 폴백). */
    private void showAddressInputDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, 0);

        // 주소는 직접 입력 대신 우편번호 검색으로 채운다(직접 수정 방지).
        EditText etAddr = new EditText(this);
        etAddr.setHint("주소 (주소 검색으로 선택)");
        etAddr.setText(selectedAddress);
        etAddr.setFocusable(false);
        etAddr.setClickable(true);

        Button btnSearchAddress = new Button(this);
        btnSearchAddress.setText("주소 검색");

        EditText etDetail = new EditText(this);
        etDetail.setHint("상세주소 (예: 101동 1001호)");
        etDetail.setText(selectedAddressDetail);

        // 주소 검색 버튼/주소칸 탭 → 다음 우편번호 검색 → 도로명 주소 채움.
        View.OnClickListener openSearch = v -> com.hackmin.app.util.PostcodeSearch.show(this,
                (zonecode, address) -> etAddr.setText(address));
        btnSearchAddress.setOnClickListener(openSearch);
        etAddr.setOnClickListener(openSearch);

        box.addView(etAddr);
        box.addView(btnSearchAddress);
        box.addView(etDetail);

        new AlertDialog.Builder(this)
                .setTitle("배송지 입력")
                .setView(box)
                .setPositiveButton("확인", (d, w) -> {
                    selectedAddress = etAddr.getText().toString().trim();
                    selectedAddressDetail = etDetail.getText().toString().trim();
                    updateAddressDisplay();
                    // 주소가 입력되면 기본 주소로 저장할지 확인한다.
                    if (!TextUtils.isEmpty(selectedAddress)) {
                        confirmSetDefaultAddress();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /** 상단 배송지 표시를 현재 선택값으로 갱신한다. */
    private void updateAddressDisplay() {
        if (TextUtils.isEmpty(selectedAddress)) {
            tvSelectedAddress.setText("배송지를 선택해주세요");
        } else {
            tvSelectedAddress.setText(
                    selectedAddress
                            + (TextUtils.isEmpty(selectedAddressDetail) ? "" : " " + selectedAddressDetail));
        }
    }

    /** "이 주소를 기본 주소로 설정하시겠습니까?" — 예 선택 시 로컬에 저장(다음 진입 시 자동 적용). */
    private void confirmSetDefaultAddress() {
        new AlertDialog.Builder(this)
                .setMessage("이 주소를 기본 주소로 설정하시겠습니까?")
                .setPositiveButton("예", (d, w) -> {
                    saveDefaultAddress(selectedAddress, selectedAddressDetail);
                    Toast.makeText(this, "기본 주소로 설정되었습니다.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("아니오", null)
                .show();
    }

    // 기본 배송지는 로컬(SharedPreferences)에 저장 → 다음 주문서 진입 시 자동 적용.
    // 계정별로 파일을 분리(prefs 이름에 user_id 부착)해 다른 계정의 배송지가 보이지 않게 한다.
    private static final String PREF_ADDR = "default_address";
    private static final String KEY_ADDR = "address";
    private static final String KEY_ADDR_DETAIL = "address_detail";

    /** 현재 로그인 계정 전용 배송지 저장소. */
    private android.content.SharedPreferences addrPrefs() {
        long uid = SessionManager.getInstance(this).getUserId();
        return getSharedPreferences(PREF_ADDR + "_" + uid, MODE_PRIVATE);
    }

    /**
     * 계정별 분리 이전에 쓰던 전역 캐시(배송지·카드)를 비운다.
     * 예전 버전에서 다른 계정이 남긴 정보가 파일로 잔존하지 않도록 최초 진입 시 한 번 정리한다.
     */
    private void clearLegacyGlobalCaches() {
        getSharedPreferences(PREF_ADDR, MODE_PRIVATE).edit().clear().apply();
        getSharedPreferences(PREF_CARDS, MODE_PRIVATE).edit().clear().apply();
    }

    /** 기본 배송지를 저장한다. */
    private void saveDefaultAddress(String address, String detail) {
        addrPrefs().edit()
                .putString(KEY_ADDR, address)
                .putString(KEY_ADDR_DETAIL, detail)
                .apply();
    }

    /** 저장된 기본 배송지가 있으면 선택값·상단 표시에 반영한다. */
    private void applySavedDefaultAddress() {
        android.content.SharedPreferences p = addrPrefs();
        String addr = p.getString(KEY_ADDR, "");
        String detail = p.getString(KEY_ADDR_DETAIL, "");
        if (!TextUtils.isEmpty(addr)) {
            selectedAddress = addr;
            selectedAddressDetail = detail;
            updateAddressDisplay();
        }
    }

    /**
     * 마이페이지 배송지 관리(서버 /me/addresses)에서 기본 배송지를 불러와 반영한다.
     * is_default 배송지가 있으면 그걸, 없으면 첫 배송지를 자동 선택한다.
     */
    private void loadServerDefaultAddress() {
        ApiClient.userApi(this).getAddresses().enqueue(new Callback<PagedResponse<AddressDto>>() {
            @Override
            public void onResponse(@NonNull Call<PagedResponse<AddressDto>> call,
                                   @NonNull Response<PagedResponse<AddressDto>> response) {
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getResults() == null) {
                    return;
                }
                List<AddressDto> list = response.body().getResults();
                AddressDto chosen = null;
                for (AddressDto a : list) {
                    if (a.isDefault()) {
                        chosen = a;
                        break;
                    }
                }
                if (chosen == null && !list.isEmpty()) {
                    chosen = list.get(0);  // 기본 지정이 없으면 첫 배송지 사용.
                }
                if (chosen != null) {
                    selectedAddress = chosen.getAddress() != null ? chosen.getAddress() : "";
                    selectedAddressDetail = chosen.getDetail() != null ? chosen.getDetail() : "";
                    updateAddressDisplay();
                }
            }

            @Override
            public void onFailure(@NonNull Call<PagedResponse<AddressDto>> call, @NonNull Throwable t) {
                // 서버 조회 실패 시 로컬 기본값(applySavedDefaultAddress)이 이미 적용돼 있으므로 무시.
            }
        });
    }

    // 요청사항 프리셋(마지막 "직접 입력"은 사용자가 직접 타이핑).
    private static final String[] REQUEST_NOTE_OPTIONS = {
            "문 앞에 놓아주세요",
            "경비실에 놓아주세요",
            "벨 누르지 말고 노크해 주세요",
            "직접 받을게요",
            "전화주시면 마중 나갈게요",
            "직접 입력",
    };

    /** 요청사항 칸을 탭하면 프리셋 선택 다이얼로그가 뜨도록 설정(직접 입력만 타이핑). */
    private void setupRequestNote() {
        etRequestMessage.setFocusable(false);
        etRequestMessage.setClickable(true);
        etRequestMessage.setOnClickListener(v -> showRequestNoteOptions());
    }

    /** 요청사항 프리셋 목록을 보여준다. */
    private void showRequestNoteOptions() {
        new AlertDialog.Builder(this)
                .setTitle("요청사항")
                .setItems(REQUEST_NOTE_OPTIONS, (d, which) -> {
                    if (which == REQUEST_NOTE_OPTIONS.length - 1) {
                        showCustomRequestNoteDialog();  // 직접 입력
                    } else {
                        etRequestMessage.setText(REQUEST_NOTE_OPTIONS[which]);
                    }
                })
                .show();
    }

    /** "직접 입력" 선택 시 자유 입력 다이얼로그. */
    private void showCustomRequestNoteDialog() {
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, pad, pad, 0);

        EditText et = new EditText(this);
        et.setHint("요청사항을 입력해주세요");
        et.setText(etRequestMessage.getText().toString());
        box.addView(et);

        new AlertDialog.Builder(this)
                .setTitle("직접 입력")
                .setView(box)
                .setPositiveButton("확인", (d, w) -> etRequestMessage.setText(et.getText().toString().trim()))
                .setNegativeButton("취소", null)
                .show();
    }

    /** 결제하기: 주문 생성(POST /orders) → 결제(POST /payments) → 주문추적 이동. */
    private void submitOrder() {
        if (submitting) return;
        if (containerOrderItems.getChildCount() == 0) {
            Toast.makeText(this, "주문할 상품이 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(selectedAddress)) {
            Toast.makeText(this, "배송지를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 간편결제(카카오/네이버)·계좌는 결제 비밀번호 6자리 검증 후 진행. 카드는 기존대로.
        if ("kakao".equals(selectedPayment) || "naver".equals(selectedPayment)
                || "account".equals(selectedPayment)) {
            promptPaymentPassword(this::proceedSubmitOrder);
        } else {
            proceedSubmitOrder();
        }
    }

    /** 실제 주문 생성/결제 진행(결제수단 확인 이후 호출). */
    private void proceedSubmitOrder() {
        submitting = true;
        btnPay.setEnabled(false);

        String note = etRequestMessage.getText().toString().trim();
        OrderCreateRequest req = new OrderCreateRequest(selectedAddress, selectedAddressDetail, note);

        orderApi.createOrder(req).enqueue(new Callback<OrderDto>() {
            @Override
            public void onResponse(@NonNull Call<OrderDto> call, @NonNull Response<OrderDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    payForOrder(response.body());
                } else if (response.code() == 400) {
                    // 최소 주문금액 미달 등 서버 검증 실패 → 서버 메시지 그대로 안내.
                    fail(extractErrorMessage(response, "최소 주문금액을 확인해주세요."));
                } else {
                    fail("주문 생성에 실패했습니다.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<OrderDto> call, @NonNull Throwable t) {
                fail("네트워크 연결 실패 (서버 확인 필요)");
            }
        });
    }

    /** 에러 응답 본문({code,message})에서 message를 뽑아낸다. 실패 시 fallback 반환. */
    private String extractErrorMessage(Response<?> response, String fallback) {
        try {
            if (response.errorBody() != null) {
                com.hackmin.app.data.model.common.ApiErrorResponse err = new com.google.gson.Gson()
                        .fromJson(response.errorBody().string(),
                                com.hackmin.app.data.model.common.ApiErrorResponse.class);
                if (err != null && err.getMessage() != null && !err.getMessage().isEmpty()) {
                    return err.getMessage();
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private void payForOrder(OrderDto order) {
        // 선택 결제수단 → 서버가 허용하는 코드로 매핑(카카오/네이버는 간편결제).
        String method = "card".equals(selectedPayment) ? "card" : "easy_pay";
        // amount 는 생략 — 서버가 주문 총액을 사용(전달 시 정확히 일치해야 함).
        PaymentCreateRequest req = new PaymentCreateRequest(order.getId(), method);

        paymentApi.createPayment(req).enqueue(new Callback<PaymentDto>() {
            @Override
            public void onResponse(@NonNull Call<PaymentDto> call, @NonNull Response<PaymentDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(OrderActivity.this, "결제가 완료되었습니다.", Toast.LENGTH_SHORT).show();
                    Intent i = new Intent(OrderActivity.this, OrderTrackingActivity.class);
                    i.putExtra(EXTRA_ORDER_ID, order.getId());
                    startActivity(i);
                    finish();
                } else {
                    // 주문은 생성됐지만 결제 실패 — 같은 주문으로 재결제 제안.
                    offerPaymentRetry(order, "결제에 실패했습니다.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<PaymentDto> call, @NonNull Throwable t) {
                offerPaymentRetry(order, "결제 중 네트워크 오류가 발생했습니다.");
            }
        });
    }

    /**
     * 결제 실패 시 재시도 다이얼로그.
     * 주문은 이미 생성(PENDING)돼 있으므로, 주문을 다시 만들지 않고
     * 같은 주문에 대해 결제(payForOrder)만 재시도한다.
     */
    private void offerPaymentRetry(OrderDto order, String message) {
        // 재시도할 수 있도록 버튼/상태를 먼저 원복.
        submitting = false;
        btnPay.setEnabled(true);
        new AlertDialog.Builder(this)
                .setTitle("결제 실패")
                .setMessage(message + "\n주문번호 " + order.getId() + " 로 다시 결제할까요?")
                .setPositiveButton("결제 재시도", (d, w) -> {
                    submitting = true;
                    btnPay.setEnabled(false);
                    payForOrder(order);
                })
                .setNegativeButton("닫기", null)
                .setCancelable(false)
                .show();
    }

    private void fail(String message) {
        submitting = false;
        btnPay.setEnabled(true);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
