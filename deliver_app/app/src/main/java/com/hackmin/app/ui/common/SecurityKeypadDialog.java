package com.hackmin.app.ui.common;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 결제 비밀번호(6자리) 입력용 보안 키패드.
 * - 숫자(0~9) 위치가 매번 무작위로 배치되고, "재배열"로 다시 섞을 수 있다.
 * - 상단에 6개의 진행 점. 6자리를 다 누르면 자동으로 완료된다.
 * - 배경색은 결제수단에 따라 카카오=노랑, 네이버=연두.
 */
public final class SecurityKeypadDialog {

    public interface OnComplete {
        void onComplete(String pin);
    }

    private SecurityKeypadDialog() {}

    public static void show(android.content.Context ctx, String provider, OnComplete onComplete) {
        final int bg;
        final int fg;
        if ("naver".equals(provider)) {
            bg = Color.parseColor("#03C75A");   // 연두(네이버)
            fg = Color.WHITE;
        } else if ("card".equals(provider)) {
            bg = Color.parseColor("#FF6F61");   // 코랄(일반 카드)
            fg = Color.WHITE;
        } else {
            bg = Color.parseColor("#FEE500");   // 노랑(카카오)
            fg = Color.parseColor("#3C1E1E");
        }
        final float d = ctx.getResources().getDisplayMetrics().density;
        final StringBuilder pin = new StringBuilder();

        final Dialog dialog = new Dialog(ctx);

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(bg);
        int padH = (int) (16 * d);
        box.setPadding(padH, (int) (24 * d), padH, (int) (24 * d));

        // 진행 점 6개
        LinearLayout dotsRow = new LinearLayout(ctx);
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        dotsRow.setGravity(Gravity.CENTER);
        dotsRow.setPadding(0, 0, 0, (int) (20 * d));
        final TextView[] dots = new TextView[6];
        for (int i = 0; i < 6; i++) {
            TextView dot = new TextView(ctx);
            dot.setText("○");
            dot.setTextSize(20);
            dot.setTextColor(fg);
            int m = (int) (6 * d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(m, 0, m, 0);
            dot.setLayoutParams(lp);
            dots[i] = dot;
            dotsRow.addView(dot);
        }
        box.addView(dotsRow);

        final Runnable updateDots = () -> {
            for (int i = 0; i < 6; i++) {
                dots[i].setText(i < pin.length() ? "●" : "○");
            }
        };

        // 숫자 버튼 10개(무작위 배치) + 재배열 + 지우기(←).
        final Button[] digitButtons = new Button[10];
        int slot = 0;
        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            for (int c = 0; c < 3; c++) {
                Button b = new Button(ctx);
                b.setAllCaps(false);
                b.setTextColor(fg);
                b.setBackgroundColor(Color.TRANSPARENT);
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        0, (int) (56 * d), 1f);
                b.setLayoutParams(blp);

                if (r == 3 && c == 0) {
                    b.setText("재배열");
                    b.setTextSize(15);
                    // onClick(재배열)은 아래 shuffle 정의 후 연결.
                    row.addView(b);
                    b.setTag("reshuffle");
                } else if (r == 3 && c == 2) {
                    b.setText("←");
                    b.setTextSize(22);
                    b.setOnClickListener(v -> {
                        if (pin.length() > 0) {
                            pin.deleteCharAt(pin.length() - 1);
                            updateDots.run();
                        }
                    });
                    row.addView(b);
                } else {
                    b.setTextSize(22);
                    digitButtons[slot++] = b;
                    b.setOnClickListener(v -> {
                        Object tag = b.getTag();
                        if (!(tag instanceof Integer)) return;
                        if (pin.length() < 6) {
                            pin.append(((Integer) tag).intValue());
                            updateDots.run();
                            if (pin.length() == 6) {
                                String result = pin.toString();
                                dialog.dismiss();
                                onComplete.onComplete(result);
                            }
                        }
                    });
                    row.addView(b);
                }
            }
            box.addView(row);
        }

        // 숫자를 섞어 각 버튼에 배치.
        final Runnable shuffle = () -> {
            List<Integer> nums = new ArrayList<>();
            for (int i = 0; i <= 9; i++) nums.add(i);
            Collections.shuffle(nums, new Random());
            for (int i = 0; i < 10; i++) {
                digitButtons[i].setText(String.valueOf(nums.get(i)));
                digitButtons[i].setTag(nums.get(i));
            }
        };
        // 재배열 버튼 연결(첫 행 마지막 행의 좌측 버튼).
        LinearLayout lastRow = (LinearLayout) box.getChildAt(box.getChildCount() - 1);
        lastRow.getChildAt(0).setOnClickListener(v -> shuffle.run());
        shuffle.run();

        dialog.setContentView(box);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(bg));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(Gravity.BOTTOM);
        }
        dialog.show();
    }
}
