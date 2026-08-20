package com.hackmin.connect.ui.common;

import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Android 15(targetSdk 35) edge-to-edge 강제 대응 공용 유틸.
 * 레이아웃 루트에 시스템 바(상태바·내비바)·디스플레이컷아웃 인셋을 패딩으로 적용해,
 * 콘텐츠가 시스템 바에 가려지지 않게 한다(안드로이드 공식 권장 패턴).
 */
public final class EdgeToEdgeUtil {

    private EdgeToEdgeUtil() {}

    /** 액티비티 콘텐츠 루트에 시스템 바 인셋을 패딩으로 적용한다. onCreate(setContentView 이후)에서 호출. */
    public static void apply(AppCompatActivity activity) {
        View contentParent = activity.findViewById(android.R.id.content);
        if (!(contentParent instanceof ViewGroup)) return;
        ViewGroup vg = (ViewGroup) contentParent;
        if (vg.getChildCount() == 0) return;
        final View root = vg.getChildAt(0);
        final int pl = root.getPaddingLeft();
        final int pt = root.getPaddingTop();
        final int pr = root.getPaddingRight();
        final int pb = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            // 하단(내비바)만 패딩 적용 — 상단바는 각 화면 레이아웃 여백을 유지(상단 잔상/이중 여백 방지).
            v.setPadding(pl + bars.left, pt, pr + bars.right, pb + bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
