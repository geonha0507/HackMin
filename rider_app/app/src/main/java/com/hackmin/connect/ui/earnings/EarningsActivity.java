package com.hackmin.connect.ui.earnings;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.connect.R;
import com.hackmin.connect.data.model.common.PagedResponse;
import com.hackmin.connect.data.model.rider.DeliveryDto;
import com.hackmin.connect.network.ApiClient;
import com.hackmin.connect.ui.common.BaseActivity;
import com.hackmin.connect.ui.common.BottomNav;
import com.hackmin.connect.util.ConnectFormat;
import com.hackmin.connect.util.DeliveryFee;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 수입 — 오늘/전체 배달 수입 요약과 완료 건 내역.
 * 건별 배달료는 {@link DeliveryFee} 고정 단가로 계산한다.
 */
public class EarningsActivity extends BaseActivity {

    private TextView tvTodayEarn, tvTodayCount, tvTotalEarn, tvTotalCount, tvEmpty;
    private EarningsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_earnings);
        BottomNav.setup(this, BottomNav.Tab.EARNINGS);

        tvTodayEarn = findViewById(R.id.tv_today_earn);
        tvTodayCount = findViewById(R.id.tv_today_count);
        tvTotalEarn = findViewById(R.id.tv_total_earn);
        tvTotalCount = findViewById(R.id.tv_total_count);
        tvEmpty = findViewById(R.id.tv_empty);

        RecyclerView rv = findViewById(R.id.rv_earnings);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EarningsAdapter();
        rv.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        ApiClient.riderApi(this).getDeliveries(null, 1).enqueue(
                new Callback<PagedResponse<DeliveryDto>>() {
            @Override
            public void onResponse(Call<PagedResponse<DeliveryDto>> call,
                                   Response<PagedResponse<DeliveryDto>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResults() != null) {
                    renderEarnings(response.body().getResults());
                } else {
                    renderEarnings(new ArrayList<>());
                }
            }

            @Override
            public void onFailure(Call<PagedResponse<DeliveryDto>> call, Throwable t) {
                tvEmpty.setVisibility(View.VISIBLE);
                tvEmpty.setText("내역을 불러오지 못했어요.\n네트워크 상태를 확인해 주세요.");
            }
        });
    }

    private void renderEarnings(List<DeliveryDto> all) {
        List<DeliveryDto> done = new ArrayList<>();
        int todayCount = 0;
        long todayFee = 0, totalFee = 0;
        for (DeliveryDto d : all) {
            if ("delivered".equals(d.getStatus())) {
                done.add(d);
                long fee = DeliveryFee.feeOf(d);   // 서버가 거리로 산정한 실제 배달료
                totalFee += fee;
                if (ConnectFormat.isToday(d.getAssignedAt())) {
                    todayCount++;
                    todayFee += fee;
                }
            }
        }
        tvTodayEarn.setText(ConnectFormat.won(todayFee));
        tvTodayCount.setText("완료 " + todayCount + "건");
        tvTotalEarn.setText(ConnectFormat.won(totalFee));
        tvTotalCount.setText("완료 " + done.size() + "건");
        adapter.submit(done);
        tvEmpty.setVisibility(done.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
