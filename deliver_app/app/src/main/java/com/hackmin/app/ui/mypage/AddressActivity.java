package com.hackmin.app.ui.mypage;

// ===== [C] START: 배송지 조회/등록 화면 (신규) - /me/addresses =====

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.app.R;
import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.user.AddressDto;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.util.PostcodeSearch;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 배송지 관리 화면.
 * - GET  /me/addresses : 목록 조회
 * - POST /me/addresses : 신규 등록
 * - DELETE /me/addresses/{id} : 삭제
 * 참고: 이 화면이 관리하는 배송지 데이터를 B의 주문서(OrderActivity) 주소선택이 소비한다.
 */
public class AddressActivity extends AppCompatActivity {

    private RecyclerView rvAddresses;
    private TextView tvEmpty;
    private Button btnAddAddress;
    private ImageButton btnBack;

    private AddressAdapter adapter;
    private final List<AddressDto> addressList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_address);
        com.hackmin.app.ui.common.BottomNav.setup(this, com.hackmin.app.ui.common.BottomNav.Tab.NONE);

        rvAddresses = findViewById(R.id.rvAddresses);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnAddAddress = findViewById(R.id.btnAddAddress);
        btnBack = findViewById(R.id.btnBack);

        rvAddresses.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AddressAdapter(addressList, this::confirmDelete, this::setDefaultAddress);
        rvAddresses.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnAddAddress.setOnClickListener(v -> showAddDialog());

        loadAddresses();
    }

    /** 배송지 목록 조회 (응답은 {count,next,previous,results} 페이지 형태) */
    private void loadAddresses() {
        ApiClient.userApi(this).getAddresses()
                .enqueue(new Callback<PagedResponse<AddressDto>>() {
                    @Override
                    public void onResponse(Call<PagedResponse<AddressDto>> call,
                                           Response<PagedResponse<AddressDto>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getResults() != null) {
                            addressList.clear();
                            addressList.addAll(response.body().getResults());
                            adapter.notifyDataSetChanged();
                            updateEmptyView();
                        } else {
                            Toast.makeText(AddressActivity.this,
                                    "배송지를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<PagedResponse<AddressDto>> call, Throwable t) {
                        Toast.makeText(AddressActivity.this,
                                "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void updateEmptyView() {
        tvEmpty.setVisibility(addressList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** 배송지 추가 다이얼로그 (라벨/주소/상세) */
    private void showAddDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, 0);

        EditText etLabel = new EditText(this);
        etLabel.setHint("배송지 이름 (예: 집, 회사)");
        etLabel.setInputType(InputType.TYPE_CLASS_TEXT);

        // 주소는 직접 입력 대신 우편번호 검색으로 채운다(직접 수정 방지).
        EditText etAddress = new EditText(this);
        etAddress.setHint("주소 (주소 검색으로 선택)");
        etAddress.setFocusable(false);
        etAddress.setClickable(true);

        Button btnSearchAddress = new Button(this);
        btnSearchAddress.setText("주소 검색");

        EditText etDetail = new EditText(this);
        etDetail.setHint("상세 주소");
        etDetail.setInputType(InputType.TYPE_CLASS_TEXT);

        // 주소 검색 버튼/주소칸 탭 → 다음 우편번호 검색 → 도로명 주소 채움.
        View.OnClickListener openSearch = v -> PostcodeSearch.show(this,
                (zonecode, address) -> etAddress.setText(address));
        btnSearchAddress.setOnClickListener(openSearch);
        etAddress.setOnClickListener(openSearch);

        container.addView(etLabel);
        container.addView(etAddress);
        container.addView(btnSearchAddress);
        container.addView(etDetail);

        new AlertDialog.Builder(this)
                .setTitle("배송지 추가")
                .setView(container)
                .setPositiveButton("등록", (dialog, which) -> {
                    String label = etLabel.getText().toString().trim();
                    String address = etAddress.getText().toString().trim();
                    String detail = etDetail.getText().toString().trim();
                    if (address.isEmpty()) {
                        Toast.makeText(this, "주소를 입력해주세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    createAddress(label, address, detail);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /** 배송지 등록 */
    private void createAddress(String label, String address, String detail) {
        // AddressDto(label, address, detail, postalCode, lat, lng, isDefault)
        AddressDto body = new AddressDto(
                label.isEmpty() ? "배송지" : label,
                address, detail, null, null, null,
                addressList.isEmpty() // 첫 배송지는 기본배송지로
        );

        ApiClient.userApi(this).createAddress(body)
                .enqueue(new Callback<AddressDto>() {
                    @Override
                    public void onResponse(Call<AddressDto> call, Response<AddressDto> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Toast.makeText(AddressActivity.this,
                                    "배송지가 등록되었습니다.", Toast.LENGTH_SHORT).show();
                            loadAddresses();
                        } else {
                            Toast.makeText(AddressActivity.this,
                                    "배송지 등록에 실패했습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<AddressDto> call, Throwable t) {
                        Toast.makeText(AddressActivity.this,
                                "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * 기본 배송지로 설정. 서버(AddressDetailView)는 단일 기본배송지를 강제하지 않으므로,
     * 클라이언트에서 대상 주소를 true로 저장한 뒤 기존 기본배송지를 false로 되돌린다.
     */
    private void setDefaultAddress(AddressDto target) {
        AddressDto updated = new AddressDto(
                target.getLabel(), target.getAddress(), target.getDetail(),
                target.getPostalCode(), target.getLatitude(), target.getLongitude(), true);

        ApiClient.userApi(this).updateAddress(target.getId(), updated)
                .enqueue(new Callback<AddressDto>() {
                    @Override
                    public void onResponse(Call<AddressDto> call, Response<AddressDto> response) {
                        if (response.isSuccessful()) {
                            clearOtherDefaults(target.getId());
                        } else {
                            Toast.makeText(AddressActivity.this,
                                    "기본 배송지 설정에 실패했습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<AddressDto> call, Throwable t) {
                        Toast.makeText(AddressActivity.this,
                                "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void clearOtherDefaults(long newDefaultId) {
        List<AddressDto> toClear = new ArrayList<>();
        for (AddressDto a : addressList) {
            if (a.isDefault() && a.getId() != newDefaultId) {
                toClear.add(a);
            }
        }
        if (toClear.isEmpty()) {
            loadAddresses();
            return;
        }
        for (AddressDto a : toClear) {
            AddressDto cleared = new AddressDto(
                    a.getLabel(), a.getAddress(), a.getDetail(),
                    a.getPostalCode(), a.getLatitude(), a.getLongitude(), false);
            ApiClient.userApi(this).updateAddress(a.getId(), cleared).enqueue(new Callback<AddressDto>() {
                @Override
                public void onResponse(Call<AddressDto> call, Response<AddressDto> response) {
                    loadAddresses();
                }

                @Override
                public void onFailure(Call<AddressDto> call, Throwable t) {
                    loadAddresses();
                }
            });
        }
    }

    private void confirmDelete(AddressDto address) {
        new AlertDialog.Builder(this)
                .setTitle("배송지 삭제")
                .setMessage((address.getLabel() != null ? address.getLabel() : "이 배송지") + "를 삭제할까요?")
                .setPositiveButton("삭제", (dialog, which) -> deleteAddress(address))
                .setNegativeButton("취소", null)
                .show();
    }

    /** 배송지 삭제 */
    private void deleteAddress(AddressDto address) {
        ApiClient.userApi(this).deleteAddress(address.getId())
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(AddressActivity.this,
                                    "삭제되었습니다.", Toast.LENGTH_SHORT).show();
                            loadAddresses();
                        } else {
                            Toast.makeText(AddressActivity.this,
                                    "삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Toast.makeText(AddressActivity.this,
                                "네트워크 오류 (서버 확인 필요)", Toast.LENGTH_LONG).show();
                    }
                });
    }
}
// ===== [C] END =====
