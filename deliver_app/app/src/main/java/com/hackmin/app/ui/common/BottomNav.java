package com.hackmin.app.ui.common;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

import com.hackmin.app.R;
import com.hackmin.app.data.model.cart.CartDto;
import com.hackmin.app.data.model.cart.CartItemDto;
import com.hackmin.app.data.model.common.PagedResponse;
import com.hackmin.app.data.model.order.OrderDto;
import com.hackmin.app.network.ApiClient;
import com.hackmin.app.ui.cart.CartActivity;
import com.hackmin.app.ui.home.HomeActivity;
import com.hackmin.app.ui.mypage.MyPageActivity;
import com.hackmin.app.ui.order.OrderHistoryActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 공용 하단 네비게이션 바(장바구니 · 홈 · 마이페이지) 연결 헬퍼.
 * 각 화면 레이아웃에 {@code <include layout="@layout/bottom_nav"/>} 를 넣고
 * onCreate에서 {@code BottomNav.setup(this, BottomNav.Tab.XXX)} 를 호출한다.
 */
public final class BottomNav {

    public enum Tab { HOME, CART, ORDERS, MYPAGE, NONE }

    private BottomNav() {}

    public static void setup(AppCompatActivity activity, Tab active) {
        View navCart = activity.findViewById(R.id.nav_cart);
        View navHome = activity.findViewById(R.id.nav_home);
        View navOrders = activity.findViewById(R.id.nav_orders);
        View navMypage = activity.findViewById(R.id.nav_mypage);
        if (navCart == null || navHome == null || navOrders == null || navMypage == null) return;

        // edge-to-edge(안드로이드 15, targetSdk 35) 대응: 레이아웃 루트에 상태바·내비바 인셋을
        // 패딩으로 적용해 콘텐츠(상단바·하단 네비게이션)가 시스템 바에 가려지지 않게 한다.
        // (안드로이드 공식 문서 권장 패턴: setOnApplyWindowInsetsListener + CONSUMED 반환)
        EdgeToEdgeUtil.apply(activity);

        navCart.setOnClickListener(v -> {
            if (active != Tab.CART) navigate(activity, CartActivity.class);
        });
        navHome.setOnClickListener(v -> {
            if (active != Tab.HOME) navigate(activity, HomeActivity.class);
        });
        navOrders.setOnClickListener(v -> {
            if (active != Tab.ORDERS) navigate(activity, OrderHistoryActivity.class);
        });
        navMypage.setOnClickListener(v -> {
            if (active != Tab.MYPAGE) navigate(activity, MyPageActivity.class);
        });

        highlight(activity, R.id.nav_cart_icon, R.id.nav_cart_label, active == Tab.CART);
        highlight(activity, R.id.nav_home_icon, R.id.nav_home_label, active == Tab.HOME);
        highlight(activity, R.id.nav_orders_icon, R.id.nav_orders_label, active == Tab.ORDERS);
        highlight(activity, R.id.nav_mypage_icon, R.id.nav_mypage_label, active == Tab.MYPAGE);

        // 화면이 보일 때마다(onResume) 뱃지 갱신 — onNewIntent로 재진입해도 최신값 반영.
        activity.getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshBadges(activity);
            }
        });
    }

    /** 장바구니 담긴 수량 / 주문내역 개수를 각 탭 뱃지에 표시한다. */
    private static void refreshBadges(AppCompatActivity activity) {
        // 장바구니: 담긴 총 수량.
        ApiClient.cartApi(activity).getCart().enqueue(new Callback<CartDto>() {
            @Override
            public void onResponse(Call<CartDto> call, Response<CartDto> response) {
                int qty = 0;
                if (response.isSuccessful() && response.body() != null
                        && response.body().getItems() != null) {
                    for (CartItemDto item : response.body().getItems()) {
                        qty += item.getQuantity();
                    }
                }
                setBadge(activity, R.id.tv_cart_badge, qty);
            }

            @Override
            public void onFailure(Call<CartDto> call, Throwable t) {
                // 뱃지 갱신 실패는 조용히 무시.
            }
        });

        // 주문내역: 전체 주문 수.
        ApiClient.orderApi(activity).getMyOrders(null, 1).enqueue(
                new Callback<PagedResponse<OrderDto>>() {
            @Override
            public void onResponse(Call<PagedResponse<OrderDto>> call,
                                   Response<PagedResponse<OrderDto>> response) {
                int count = 0;
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResults() != null) {
                    // 주문내역 탭에서 확인하지 않은(미확인) 주문만 센다.
                    java.util.Set<String> seen =
                            com.hackmin.app.util.OrderSeenStore.getSeenIds(activity);
                    for (OrderDto o : response.body().getResults()) {
                        if (!seen.contains(String.valueOf(o.getId()))) {
                            count++;
                        }
                    }
                }
                setBadge(activity, R.id.tv_orders_badge, count);
            }

            @Override
            public void onFailure(Call<PagedResponse<OrderDto>> call, Throwable t) {
                // 뱃지 갱신 실패는 조용히 무시.
            }
        });
    }

    /** 뱃지 숫자 표시. 0이면 숨김, 99 초과면 "99+". */
    private static void setBadge(AppCompatActivity activity, int badgeId, int count) {
        TextView badge = activity.findViewById(badgeId);
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
