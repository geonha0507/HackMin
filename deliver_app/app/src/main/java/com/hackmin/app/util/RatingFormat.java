package com.hackmin.app.util;

import java.util.Locale;

/** 별점(0.5 단위 포함) 표시 포맷 공통 유틸. */
public final class RatingFormat {

    private RatingFormat() {}

    /** 예: 5.0 → "★ 5", 4.5 → "★ 4.5" */
    public static String stars(double rating) {
        return "★ " + number(rating);
    }

    /** 소수점 이하가 0이면 정수로, 아니면 소수 첫째자리까지. */
    public static String number(double rating) {
        if (rating == Math.rint(rating)) {
            return String.valueOf((int) rating);
        }
        return String.format(Locale.KOREA, "%.1f", rating);
    }
}
