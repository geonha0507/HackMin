package com.hackmin.connect.ui.delivery;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.hackmin.connect.R;
import com.hackmin.connect.data.model.common.PagedResponse;
import com.hackmin.connect.data.model.rider.DeliveryDto;
import com.hackmin.connect.network.ApiClient;
import com.hackmin.connect.network.SessionManager;
import com.hackmin.connect.ui.common.BaseActivity;
import com.hackmin.connect.ui.common.BottomNav;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 운행 — 콜 목록. 상태 칩(전체/신규 콜/진행 중/완료)으로 필터링한다.
 * 운행 대기 상태면 상단에 안내 배너를 띄운다.
 */
public class DeliveryListActivity extends BaseActivity {

    private DeliveryAdapter adapter;
    private TextView tvEmpty;
    private View bannerOffDuty;
    private ChipGroup chipGroup;
    private final List<DeliveryDto> all = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivery_list);
        BottomNav.setup(this, BottomNav.Tab.DELIVERY);

        tvEmpty = findViewById(R.id.tv_empty);
        bannerOffDuty = findViewById(R.id.banner_off_duty);
        chipGroup = findViewById(R.id.chip_group);

        RecyclerView rv = findViewById(R.id.rv_deliveries);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeliveryAdapter(d -> {
            Intent i = new Intent(this, DeliveryDetailActivity.class);
            i.putExtra(DeliveryDetailActivity.EXTRA_DELIVERY_ID, d.getId());
            // 상세 API 응답에는 가게 이름이 없어 목록에서 함께 넘긴다.
            i.putExtra(DeliveryDetailActivity.EXTRA_RESTAURANT, d.getRestaurant());
            i.putExtra(DeliveryDetailActivity.EXTRA_TOTAL, d.getTotal());
            startActivity(i);
        });
        rv.setAdapter(adapter);

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilter());
        bannerOffDuty.setOnClickListener(v -> finish()); // 홈으로 돌아가 운행 시작
    }

    @Override
    protected void onResume() {
        super.onResume();
        bannerOffDuty.setVisibility(
                SessionManager.getInstance(this).isOnDuty() ? View.GONE : View.VISIBLE);
        load();
    }

    private void load() {
        ApiClient.riderApi(this).getDeliveries(null, 1).enqueue(
                new Callback<PagedResponse<DeliveryDto>>() {
            @Override
            public void onResponse(Call<PagedResponse<DeliveryDto>> call,
                                   Response<PagedResponse<DeliveryDto>> response) {
                all.clear();
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResults() != null) {
                    all.addAll(response.body().getResults());
                }
                applyFilter();
            }

            @Override
            public void onFailure(Call<PagedResponse<DeliveryDto>> call, Throwable t) {
                all.clear();
                applyFilter();
                tvEmpty.setText("목록을 불러오지 못했어요.\n네트워크 상태를 확인해 주세요.");
            }
        });
    }

    /** 선택된 칩에 맞는 상태만 추려 어댑터에 반영한다. */
    private void applyFilter() {
        int checked = chipGroup.getCheckedChipId();
        List<DeliveryDto> filtered = new ArrayList<>();
        for (DeliveryDto d : all) {
            String s = d.getStatus() == null ? "" : d.getStatus();
            boolean match;
            if (checked == R.id.chip_new) {
                match = s.equals("assigned");
            } else if (checked == R.id.chip_progress) {
                match = s.equals("picked_up") || s.equals("delivering");
            } else if (checked == R.id.chip_done) {
                match = s.equals("delivered");
            } else {
                match = true; // 전체
            }
            if (match) filtered.add(d);
        }
        adapter.submit(filtered);
        tvEmpty.setText("표시할 콜이 없어요");
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
