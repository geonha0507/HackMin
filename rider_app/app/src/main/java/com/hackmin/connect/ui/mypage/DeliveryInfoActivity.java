package com.hackmin.connect.ui.mypage;

import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.hackmin.connect.R;
import com.hackmin.connect.data.model.rider.RiderProfileDto;
import com.hackmin.connect.network.ApiClient;
import com.hackmin.connect.ui.common.BaseActivity;
import com.hackmin.connect.ui.common.ClickGuard;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 배달 전 정보 등록(정산 계좌·예금주·면허번호·차량번호·희망지역·배달수단).
 * 서버 /rider/profile 로 조회·저장한다. 계좌번호는 저장 시 암호화되고 조회 시 마스킹된다.
 */
public class DeliveryInfoActivity extends BaseActivity {

    // 정산 계좌 선택용 은행 목록.
    private static final String[] BANKS = {
            "국민은행", "신한은행", "우리은행", "하나은행", "농협은행",
            "기업은행", "카카오뱅크", "토스뱅크", "케이뱅크", "SC제일은행",
            "부산은행", "대구은행", "새마을금고", "우체국",
    };

    private TextInputEditText etBank, etAccount, etHolder, etLicense, etVehicle, etRegion;
    private TextView tvAccountMasked;
    private RadioGroup rgMethod;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivery_info);

        etBank = findViewById(R.id.et_bank_name);
        etAccount = findViewById(R.id.et_account_number);
        etHolder = findViewById(R.id.et_account_holder);
        etLicense = findViewById(R.id.et_license_number);
        etVehicle = findViewById(R.id.et_vehicle_number);
        etRegion = findViewById(R.id.et_region);
        tvAccountMasked = findViewById(R.id.tv_account_masked);
        rgMethod = findViewById(R.id.rg_method);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        etBank.setOnClickListener(v -> showBankPicker());
        etRegion.setOnClickListener(v -> com.hackmin.connect.util.RegionSearch.show(
                this, region -> etRegion.setText(region)));

        load();
    }

    private void load() {
        ApiClient.riderApi(this).getProfile().enqueue(new Callback<RiderProfileDto>() {
            @Override
            public void onResponse(Call<RiderProfileDto> call, Response<RiderProfileDto> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                RiderProfileDto p = response.body();
                etBank.setText(nz(p.getBankName()));
                etHolder.setText(nz(p.getAccountHolder()));
                etLicense.setText(nz(p.getLicenseNumber()));
                etVehicle.setText(nz(p.getVehicleNumber()));
                etRegion.setText(nz(p.getRegion()));
                selectMethod(p.getDeliveryMethod());

                // 계좌번호는 평문을 내려주지 않으므로 마스킹값만 안내로 표시(변경 시 다시 입력).
                if (p.getAccountNumberMasked() != null && !p.getAccountNumberMasked().isEmpty()) {
                    tvAccountMasked.setText("등록된 계좌: " + p.getAccountNumberMasked()
                            + " (변경하려면 새로 입력)");
                    tvAccountMasked.setVisibility(android.view.View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<RiderProfileDto> call, Throwable t) {
                // 최초 등록 전이거나 네트워크 실패 — 빈 폼 유지.
            }
        });
    }

    private void save() {
        if (!ClickGuard.allow()) return;

        RiderProfileDto dto = new RiderProfileDto();
        dto.setBankName(text(etBank));
        dto.setAccountHolder(text(etHolder));
        dto.setLicenseNumber(text(etLicense));
        dto.setVehicleNumber(text(etVehicle));
        dto.setRegion(text(etRegion));
        dto.setDeliveryMethod(selectedMethod());

        // 계좌번호는 입력했을 때만 전송(비우면 기존 값 유지).
        String account = text(etAccount);
        if (!account.isEmpty()) {
            dto.setAccountNumber(account);
        }

        Button btnSave = findViewById(R.id.btn_save);
        btnSave.setEnabled(false);
        ApiClient.riderApi(this).updateProfile(dto).enqueue(new Callback<RiderProfileDto>() {
            @Override
            public void onResponse(Call<RiderProfileDto> call, Response<RiderProfileDto> response) {
                btnSave.setEnabled(true);
                if (response.isSuccessful()) {
                    Toast.makeText(DeliveryInfoActivity.this, "배달 정보가 저장됐어요.", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(DeliveryInfoActivity.this, "저장 실패 (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<RiderProfileDto> call, Throwable t) {
                btnSave.setEnabled(true);
                Toast.makeText(DeliveryInfoActivity.this, "네트워크 연결 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showBankPicker() {
        String current = text(etBank);
        int checked = -1;
        for (int i = 0; i < BANKS.length; i++) {
            if (BANKS[i].equals(current)) { checked = i; break; }
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("은행 선택")
                .setSingleChoiceItems(BANKS, checked, (dialog, which) -> {
                    etBank.setText(BANKS[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private String selectedMethod() {
        int id = rgMethod.getCheckedRadioButtonId();
        if (id == R.id.rb_walk) return "walk";
        if (id == R.id.rb_bicycle) return "bicycle";
        if (id == R.id.rb_motorcycle) return "motorcycle";
        if (id == R.id.rb_car) return "car";
        return "";
    }

    private void selectMethod(String method) {
        if (method == null) return;
        switch (method) {
            case "walk": rgMethod.check(R.id.rb_walk); break;
            case "bicycle": rgMethod.check(R.id.rb_bicycle); break;
            case "motorcycle": rgMethod.check(R.id.rb_motorcycle); break;
            case "car": rgMethod.check(R.id.rb_car); break;
            default: break;
        }
    }

    private static String text(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
