package com.hackmin.connect.ui.common;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

import com.hackmin.connect.R;
import com.hackmin.connect.data.model.common.PagedResponse;
import com.hackmin.connect.data.model.rider.DeliveryDto;
import com.hackmin.connect.network.ApiClient;
import com.hackmin.connect.ui.delivery.DeliveryListActivity;
import com.hackmin.connect.ui.earnings.EarningsActivity;
import com.hackmin.connect.ui.home.HomeActivity;
import com.hackmin.connect.ui.mypage.MyPageActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 공용 하단 네비게이션 바(홈 · 운행 · 수입 · 내정보) 연결 헬퍼.
 * 각 화면 레이아웃에 {@code <include layout="@layout/bottom_nav"/>} 를 넣고
 * onCreate에서 {@code BottomNav.setup(this, BottomNav.Tab.XXX)} 를 호출한다.
 */
public final class BottomNav {

    public enum Tab { HOME, DELIVERY, EARNINGS, MYPAGE, NONE }

    private BottomNav() {}

    public static void setup(AppCompatActivity activity, Tab active) {
        View navHome = activity.findViewById(R.id.nav_home);
        View navDelivery = activity.findViewById(R.id.nav_delivery);
        View navEarnings = activity.findViewById(R.id.nav_earnings);
        View navMypage = activity.findViewById(R.id.nav_mypage);
        if (navHome == null || navDelivery == null || navEarnings == null || navMypage == null) return;

        // edge-to-edge(안드로이드 15, targetSdk 35) 대응 — deliver_app과 동일 패턴.
        EdgeToEdgeUtil.apply(activity);

        navHome.setOnClickListener(v -> {
            if (active != Tab.HOME) navigate(activity, HomeActivity.class);
        });
        navDelivery.setOnClickListener(v -> {
            if (active != Tab.DELIVERY) navigate(activity, DeliveryListActivity.class);
        });
        navEarnings.setOnClickListener(v -> {
            if (active != Tab.EARNINGS) navigate(activity, EarningsActivity.class);
        });
        navMypage.setOnClickListener(v -> {
            if (active != Tab.MYPAGE) navigate(activity, MyPageActivity.class);
        });

        highlight(activity, R.id.nav_home_icon, R.id.nav_home_label, active == Tab.HOME);
        highlight(activity, R.id.nav_delivery_icon, R.id.nav_delivery_label, active == Tab.DELIVERY);
        highlight(activity, R.id.nav_earnings_icon, R.id.nav_earnings_label, active == Tab.EARNINGS);
        highlight(activity, R.id.nav_mypage_icon, R.id.nav_mypage_label, active == Tab.MYPAGE);

        // 화면이 보일 때마다(onResume) 신규 콜 뱃지 갱신.
        activity.getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshBadge(activity);
            }
        });
    }

    /** 수락 대기(assigned) 상태인 신규 콜 수를 운행 탭 뱃지에 표시한다. */
    private static void refreshBadge(AppCompatActivity activity) {
        ApiClient.riderApi(activity).getDeliveries(null, 1).enqueue(
                new Callback<PagedResponse<DeliveryDto>>() {
            @Override
            public void onResponse(Call<PagedResponse<DeliveryDto>> call,
                                   Response<PagedResponse<DeliveryDto>> response) {
                int count = 0;
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResults() != null) {
                    for (DeliveryDto d : response.body().getResults()) {
                        if ("assigned".equals(d.getStatus())) count++;
                    }
                }
                setBadge(activity, count);
            }

            @Override
            public void onFailure(Call<PagedResponse<DeliveryDto>> call, Throwable t) {
                // 뱃지 갱신 실패는 조용히 무시.
            }
        });
    }

    /** 뱃지 숫자 표시. 0이면 숨김, 99 초과면 "99+". */
    private static void setBadge(AppCompatActivity activity, int count) {
        TextView badge = activity.findViewById(R.id.tv_delivery_badge);
        if (badge == null) return;
        if (count > 0) {
            badge.setText(count > 99 ? "99+" : String.valueOf(count));
            badge.setVisibility(View.VISIBLE);
        } else {
            badge.setVisibility(View.GONE);
        }
    }

    /** 탭 간 이동: 이미 스택에 있으면 앞으로 가져오고 중간 화면은 정리(탭 UX). */
    private static void navigate(AppCompatActivity activity, Class<?> target) {
        Intent intent = new Intent(activity, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
    }

    private static void highlight(AppCompatActivity activity, int iconId, int labelId, boolean active) {
        int color = ContextCompat.getColor(activity,
                active ? R.color.coral_primary : R.color.text_secondary);
        ImageView icon = activity.findViewById(iconId);
        TextView label = activity.findViewById(labelId);
        if (icon != null) icon.setImageTintList(ColorStateList.valueOf(color));
        if (label != null) label.setTextColor(color);
    }
}
