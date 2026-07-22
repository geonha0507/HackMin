package com.hackmin.app.ui.common;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.hackmin.app.R;
import com.hackmin.app.ui.cart.CartActivity;
import com.hackmin.app.ui.home.HomeActivity;
import com.hackmin.app.ui.mypage.MyPageActivity;
import com.hackmin.app.ui.order.OrderHistoryActivity;

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
