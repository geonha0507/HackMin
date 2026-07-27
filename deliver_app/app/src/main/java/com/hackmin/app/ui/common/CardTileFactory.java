package com.hackmin.app.ui.common;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import java.util.Random;

/**
 * 결제화면의 카드 타일을 다른 화면에서도 동일하게 만드는 공용 팩토리.
 * - create(): 그라데이션 "MY CARD" 타일(일반 카드용)
 * - createImageTile(): 카카오/네이버 등 브랜드 이미지 타일
 * 두 타일 모두 작은 크기 + 좌우 스크롤 목록에 넣기 좋게 우측 마진을 둔다.
 */
public final class CardTileFactory {

    private static final int TILE_W = 190;  // dp
    private static final int TILE_H = 115;  // dp

    private CardTileFactory() {}

    /** 그라데이션 카드 타일(일반 카드). */
    public static View create(Context ctx, String name, String masked, boolean isDefault,
                              Runnable onTap, Runnable onDelete) {
        float d = ctx.getResources().getDisplayMetrics().density;

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutParams(tileParams(d));
        int p = (int) (16 * d);
        card.setPadding(p, p, p, p);

        int[] colors = randomGradientColors();
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{colors[0], colors[1]});
        bg.setCornerRadius(16 * d);
        card.setBackground(bg);

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(ctx);
        title.setText("MY CARD");
        title.setTextColor(Color.WHITE);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setTextSize(13);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(title);
        if (isDefault) top.addView(defaultBadge(ctx, d, Color.WHITE, Color.parseColor("#333333")));
        if (onDelete != null) top.addView(deleteX(ctx, d, Color.WHITE, onDelete));
        card.addView(top);

        View spacer = new View(ctx);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        card.addView(spacer);

        TextView num = new TextView(ctx);
        num.setText(masked);
        num.setTextColor(Color.WHITE);
        num.setTextSize(14);
        card.addView(num);

        TextView nameTv = new TextView(ctx);
        nameTv.setText(name);
        nameTv.setTextColor(Color.parseColor("#F0F0F0"));
        nameTv.setTextSize(11);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = (int) (2 * d);
        nameTv.setLayoutParams(nameLp);
        card.addView(nameTv);

        if (onTap != null) card.setOnClickListener(v -> onTap.run());
        return card;
    }

    /** 브랜드 이미지 타일(카카오페이/네이버페이 등). 이미지 위에 마스킹번호·X·기본배지를 겹쳐 표시. */
    public static View createImageTile(Context ctx, int imageRes, String masked, boolean isDefault,
                                       Runnable onTap, Runnable onDelete) {
        float d = ctx.getResources().getDisplayMetrics().density;

        MaterialCardView cardView = new MaterialCardView(ctx);
        cardView.setLayoutParams(tileParams(d));
        cardView.setRadius(16 * d);
        cardView.setCardElevation(0f);

        FrameLayout frame = new FrameLayout(ctx);
        frame.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ImageView img = new ImageView(ctx);
        img.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setImageResource(imageRes);
        frame.addView(img);

        // 마스킹 번호(하단, 반투명 어두운 배경으로 가독성 확보)
        TextView num = new TextView(ctx);
        num.setText(masked);
        num.setTextColor(Color.WHITE);
        num.setTextSize(13);
        num.setPadding((int) (12 * d), (int) (6 * d), (int) (12 * d), (int) (8 * d));
        num.setBackgroundColor(Color.parseColor("#66000000"));
        FrameLayout.LayoutParams numLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        numLp.gravity = Gravity.BOTTOM;
        num.setLayoutParams(numLp);
        frame.addView(num);

        // X 삭제(우상단)
        if (onDelete != null) {
            TextView del = deleteX(ctx, d, Color.WHITE, onDelete);
            FrameLayout.LayoutParams delLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            delLp.gravity = Gravity.TOP | Gravity.END;
            delLp.setMargins(0, (int) (8 * d), (int) (8 * d), 0);
            del.setLayoutParams(delLp);
            frame.addView(del);
        }

        // 기본 배지(좌상단)
        if (isDefault) {
            TextView badge = defaultBadge(ctx, d, Color.WHITE, Color.parseColor("#333333"));
            FrameLayout.LayoutParams bLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            bLp.gravity = Gravity.TOP | Gravity.START;
            bLp.setMargins((int) (8 * d), (int) (8 * d), 0, 0);
            badge.setLayoutParams(bLp);
            frame.addView(badge);
        }

        cardView.addView(frame);
        if (onTap != null) cardView.setOnClickListener(v -> onTap.run());
        return cardView;
    }

    // ── 공통 조각 ────────────────────────────────────────────
    private static LinearLayout.LayoutParams tileParams(float d) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) (TILE_W * d), (int) (TILE_H * d));
        lp.setMarginEnd((int) (12 * d));
        return lp;
    }

    private static TextView deleteX(Context ctx, float d, int color, Runnable onDelete) {
        TextView del = new TextView(ctx);
        del.setText("✕");
        del.setTextColor(color);
        del.setTextSize(15);
        int xp = (int) (4 * d);
        del.setPadding(xp, 0, xp, xp);
        del.setOnClickListener(v -> onDelete.run());
        return del;
    }

    private static TextView defaultBadge(Context ctx, float d, int bgColor, int textColor) {
        TextView badge = new TextView(ctx);
        badge.setText("기본");
        badge.setTextColor(textColor);
        badge.setTextSize(10);
        badge.setPadding((int) (7 * d), (int) (2 * d), (int) (7 * d), (int) (2 * d));
        GradientDrawable bbg = new GradientDrawable();
        bbg.setColor(bgColor);
        bbg.setCornerRadius(10 * d);
        badge.setBackground(bbg);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.setMarginEnd((int) (6 * d));
        badge.setLayoutParams(blp);
        return badge;
    }

    private static int[] randomGradientColors() {
        Random r = new Random();
        float hue = r.nextInt(360);
        int c1 = Color.HSVToColor(new float[]{hue, 0.55f, 0.85f});
        int c2 = Color.HSVToColor(new float[]{(hue + 40f) % 360f, 0.7f, 0.6f});
        return new int[]{c1, c2};
    }
}
