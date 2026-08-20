package com.hackmin.connect.ui.home;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hackmin.connect.R;
import com.hackmin.connect.data.model.common.PagedResponse;
import com.hackmin.connect.data.model.rider.DeliveryDto;
import com.hackmin.connect.data.model.rider.RiderLocationDto;
import com.hackmin.connect.network.ApiClient;
import com.hackmin.connect.network.SessionManager;
import com.hackmin.connect.ui.common.BaseActivity;
import com.hackmin.connect.ui.common.BottomNav;
import com.hackmin.connect.ui.delivery.DeliveryListActivity;
import com.hackmin.connect.util.ConnectFormat;
import com.hackmin.connect.util.DeliveryFee;
import com.hackmin.connect.util.LocationTracker;

import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 홈 — 운행 시작/종료 토글 + 오늘의 활동 요약(완료 건수·배달 수입) + 신규 콜 안내.
 * 배민커넥트 홈과 같은 구성: 인사 카드, 요약 타일 2개, 하단 고정 운행 버튼.
 */
public class HomeActivity extends BaseActivity {

    private static final int REQ_LOCATION = 1001;
    // 서버 위치 전송 최소 간격(ms). LocationTracker는 더 자주 갱신하지만 서버 부하를 줄인다.
    private static final long SEND_INTERVAL_MS = 8000L;

    private SessionManager session;
    private TextView tvGreeting, tvDutyState, tvTodayCount, tvTodayEarn, tvNewCalls;
    private TextView tvLocationState, tvLocationAddress, tvLocationCoords;
    private View cardNewCalls, dotLive;
    private Button btnDuty;

    private LocationTracker locationTracker;
    private long lastSentAt = 0L;
    private MenuAdapter menuAdapter;
    private View sectionMenus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        BottomNav.setup(this, BottomNav.Tab.HOME);

        session = SessionManager.getInstance(this);

        tvGreeting = findViewById(R.id.tv_greeting);
        tvDutyState = findViewById(R.id.tv_duty_state);
        tvTodayCount = findViewById(R.id.tv_today_count);
        tvTodayEarn = findViewById(R.id.tv_today_earn);
        tvNewCalls = findViewById(R.id.tv_new_calls);
        cardNewCalls = findViewById(R.id.card_new_calls);
        btnDuty = findViewById(R.id.btn_duty);
        tvLocationState = findViewById(R.id.tv_location_state);
        tvLocationAddress = findViewById(R.id.tv_location_address);
        tvLocationCoords = findViewById(R.id.tv_location_coords);
        dotLive = findViewById(R.id.dot_live);

        // 해킹의 민족 인기 메뉴 (가로 스크롤).
        sectionMenus = findViewById(R.id.section_menus);
        RecyclerView rvMenus = findViewById(R.id.rv_menus);
        rvMenus.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        menuAdapter = new MenuAdapter();
        rvMenus.setAdapter(menuAdapter);

        // 실시간 위치 추적기. 위치가 갱신될 때마다 카드에 주소·좌표를 그린다.
        locationTracker = new LocationTracker(this, new LocationTracker.Callback() {
            @Override
            public void onLocation(double latitude, double longitude, float accuracyMeters) {
                dotLive.setVisibility(View.VISIBLE);
                tvLocationState.setText(accuracyMeters > 0
                        ? "실시간 추적 중 · 오차 ±" + Math.round(accuracyMeters) + "m"
                        : "실시간 추적 중");
                tvLocationState.setTextColor(getColor(R.color.text_terminal_green));
                tvLocationCoords.setVisibility(View.VISIBLE);
                tvLocationCoords.setText(String.format(Locale.US, "%.6f, %.6f", latitude, longitude));
                sendLocationToServer(latitude, longitude, accuracyMeters);
            }

            @Override
            public void onAddress(String address) {
                tvLocationAddress.setText(address);
            }
        });

        String nickname = session.getNickname();
        tvGreeting.setText((nickname.isEmpty() ? "라이더" : nickname) + " 라이더님,\n오늘도 안전 운행하세요!");

        btnDuty.setOnClickListener(v -> {
            boolean next = !session.isOnDuty();
            session.setOnDuty(next);
            renderDuty();
            if (next) {
                // 운행 시작 → 실시간 위치 추적 시작(권한 없으면 요청) + 콜 목록 안내.
                startLocationTracking();
                startActivity(new Intent(this, DeliveryListActivity.class));
            } else {
                stopLocationTracking();
            }
        });

        cardNewCalls.setOnClickListener(v ->
                startActivity(new Intent(this, DeliveryListActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderDuty();
        loadSummary();
        loadMenus();
        // 운행 중이면 화면 복귀 시 추적을 (재)개한다.
        if (session.isOnDuty()) {
            startLocationTracking();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 화면이 안 보이면 위치 구독을 멈춰 배터리를 아낀다(운행 상태는 유지).
        stopLocationTracking();
    }

    // ── 실시간 위치 ─────────────────────────────────────────

    private void startLocationTracking() {
        if (!LocationTracker.hasPermission(this)) {
            tvLocationState.setText("권한 필요");
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
            }, REQ_LOCATION);
            return;
        }
        boolean ok = locationTracker.start();
        if (!ok) {
            tvLocationState.setText("위치 사용 불가");
            tvLocationAddress.setText("기기 위치(GPS)를 켜주세요");
        }
    }

    private void stopLocationTracking() {
        locationTracker.stop();
        dotLive.setVisibility(View.GONE);
        if (!session.isOnDuty()) {
            tvLocationState.setText("위치 꺼짐");
            tvLocationState.setTextColor(getColor(R.color.text_secondary));
        }
    }

    /**
     * 현재 위치를 서버(/rider/location)로 전송한다. 운행 중일 때만, 최소
     * {@link #SEND_INTERVAL_MS} 간격으로 보내 서버 부하와 배터리를 아낀다.
     * 전송 실패는 조용히 무시(다음 갱신에서 재시도) — 화면 표시는 이미 됐다.
     */
    private void sendLocationToServer(double lat, double lng, float accuracy) {
        if (!session.isOnDuty()) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastSentAt < SEND_INTERVAL_MS) return;
        lastSentAt = now;

        RiderLocationDto body = new RiderLocationDto(lat, lng, accuracy > 0 ? (double) accuracy : null);
        ApiClient.riderApi(this).updateLocation(body).enqueue(new Callback<RiderLocationDto>() {
            @Override
            public void onResponse(Call<RiderLocationDto> call, Response<RiderLocationDto> response) {
                // 성공/실패 모두 UI엔 영향 없음(좌표는 이미 표시). 실패 시 다음 주기에 재시도.
                if (!response.isSuccessful()) {
                    lastSentAt = 0L; // 다음 콜백에서 즉시 재시도되도록 스로틀 해제
                }
            }

            @Override
            public void onFailure(Call<RiderLocationDto> call, Throwable t) {
                lastSentAt = 0L;
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_LOCATION) return;
        boolean granted = false;
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_GRANTED) granted = true;
        }
        if (granted) {
            startLocationTracking();
        } else {
            tvLocationState.setText("권한 거부됨");
            tvLocationAddress.setText("설정에서 위치 권한을 허용하면 실시간 추적이 켜져요");
            Toast.makeText(this, "위치 권한이 없어 실시간 추적을 켤 수 없어요.", Toast.LENGTH_LONG).show();
        }
    }

    /** 운행 상태에 따라 버튼·상태 칩 문구/색을 갱신한다. */
    private void renderDuty() {
        boolean onDuty = session.isOnDuty();
        tvDutyState.setText(onDuty ? "운행 중" : "운행 대기");
        tvDutyState.setBackgroundResource(onDuty
                ? R.drawable.bg_chip_on_duty : R.drawable.bg_chip_off_duty);
        btnDuty.setText(onDuty ? "운행 종료하기" : "운행 시작하기");
        btnDuty.setBackgroundTintList(getColorStateList(onDuty
                ? R.color.text_primary : R.color.coral_primary));
    }

    /** 배달 목록 한 번으로 오늘 완료 건수·수입·신규 콜 수를 모두 계산한다. */
    private void loadSummary() {
        ApiClient.riderApi(this).getDeliveries(null, 1).enqueue(
                new Callback<PagedResponse<DeliveryDto>>() {
            @Override
            public void onResponse(Call<PagedResponse<DeliveryDto>> call,
                                   Response<PagedResponse<DeliveryDto>> response) {
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getResults() == null) {
                    return;
                }
                renderSummary(response.body().getResults());
            }

            @Override
            public void onFailure(Call<PagedResponse<DeliveryDto>> call, Throwable t) {
                // 홈 요약 로딩 실패는 조용히 무시(다음 onResume에서 재시도).
            }
        });
    }

    /**
     * 홈에 노출할 해킹의 민족 인기 메뉴를 서버(/rider/menus)에서 불러와 표시한다.
     * 실제 메뉴명·가격·사진(매장 이미지)이 내려오며, 데이터가 없거나 실패하면 섹션을 숨긴다.
     */
    private void loadMenus() {
        ApiClient.riderApi(this).getMenus(20).enqueue(
                new Callback<PagedResponse<com.hackmin.connect.data.model.rider.MenuDto>>() {
            @Override
            public void onResponse(Call<PagedResponse<com.hackmin.connect.data.model.rider.MenuDto>> call,
                                   Response<PagedResponse<com.hackmin.connect.data.model.rider.MenuDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    renderMenus(response.body().getResults());
                } else {
                    renderMenus(null);
                }
            }

            @Override
            public void onFailure(Call<PagedResponse<com.hackmin.connect.data.model.rider.MenuDto>> call, Throwable t) {
                renderMenus(null); // 실패 시 섹션 숨김(홈 나머지는 정상 표시)
            }
        });
    }

    private void renderMenus(java.util.List<com.hackmin.connect.data.model.rider.MenuDto> menus) {
        if (menus == null || menus.isEmpty()) {
            sectionMenus.setVisibility(View.GONE);
            return;
        }
        sectionMenus.setVisibility(View.VISIBLE);
        menuAdapter.submit(menus);
    }

    private void renderSummary(java.util.List<DeliveryDto> deliveries) {
        int todayDone = 0;
        int newCalls = 0;
        for (DeliveryDto d : deliveries) {
            if ("delivered".equals(d.getStatus())
                    && ConnectFormat.isToday(d.getAssignedAt())) {
                todayDone++;
            }
            if ("assigned".equals(d.getStatus())) {
                newCalls++;
            }
        }
        tvTodayCount.setText(todayDone + "건");
        tvTodayEarn.setText(ConnectFormat.won(DeliveryFee.earned(todayDone)));
        tvNewCalls.setText(newCalls > 0
                ? "대기 중인 신규 콜 " + newCalls + "건"
                : "대기 중인 신규 콜이 없어요");
    }
}
