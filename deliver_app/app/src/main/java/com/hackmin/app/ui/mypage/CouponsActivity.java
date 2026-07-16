package com.hackmin.app.ui.mypage;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.promotion.RegisterCouponRequest;
import com.hackmin.app.data.model.promotion.UserCouponDto;
import com.hackmin.app.network.ApiClient;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 보유 쿠폰 목록 + 코드 등록 화면.
 * - GET  /me/coupons
 * - POST /coupons/register
 */
public class CouponsActivity extends AppCompatActivity {

    private RecyclerView rvCoupons;
    private TextView tvEmpty;
    private EditText etCouponCode;
    private CouponAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_coupons);

        ImageButton btnBack = findViewById(R.id.btnBack);
        Button btnRegister = findViewById(R.id.btnRegister);
        etCouponCode = findViewById(R.id.etCouponCode);
        rvCoupons = findViewById(R.id.rvCoupons);
        tvEmpty = findViewById(R.id.tvEmpty);

        adapter = new CouponAdapter();
        rvCoupons.setLayoutManager(new LinearLayoutManager(this));
        rvCoupons.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnRegister.setOnClickListener(v -> registerCoupon());

        loadCoupons();
    }

    private void loadCoupons() {
        ApiClient.promotionApi(this).getMyCoupons(null)
                .enqueue(new Callback<PagedResponse<UserCouponDto>>() {
                    @Override
                    public void onResponse(Call<PagedResponse<UserCouponDto>> call,
                                           Response<PagedResponse<UserCouponDto>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<UserCouponDto> results = response.body().getResults();
                            adapter.submit(results);
                            tvEmpty.setVisibility(
                                    results == null || results.isEmpty() ? View.VISIBLE : View.GONE);
                        } else {
                            Toast.makeText(CouponsActivity.this,
                                    "쿠폰을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<PagedResponse<UserCouponDto>> call, Throwable t) {
                        Toast.makeText(CouponsActivity.this,
                                "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void registerCoupon() {
        String code = etCouponCode.getText().toString().trim();
        if (code.isEmpty()) {
            Toast.makeText(this, "쿠폰 코드를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        ApiClient.promotionApi(this).registerCoupon(new RegisterCouponRequest(code))
                .enqueue(new Callback<UserCouponDto>() {
                    @Override
                    public void onResponse(Call<UserCouponDto> call, Response<UserCouponDto> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(CouponsActivity.this,
                                    "쿠폰이 등록되었습니다.", Toast.LENGTH_SHORT).show();
                            etCouponCode.setText("");
                            loadCoupons();
                        } else if (response.code() == 409) {
                            Toast.makeText(CouponsActivity.this,
                                    "이미 등록된 쿠폰입니다.", Toast.LENGTH_SHORT).show();
                        } else if (response.code() == 404) {
                            Toast.makeText(CouponsActivity.this,
                                    "유효하지 않은 쿠폰 코드입니다.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(CouponsActivity.this,
                                    "쿠폰 등록에 실패했습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<UserCouponDto> call, Throwable t) {
                        Toast.makeText(CouponsActivity.this,
                                "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_LONG).show();
                    }
                });
    }
}
