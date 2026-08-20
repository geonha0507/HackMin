package com.hackmin.connect.util;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 금액·시간 표시 공용 포맷터. */
public final class ConnectFormat {

    private ConnectFormat() {}

    /** 3,500 → "3,500원" */
    public static String won(long amount) {
        return String.format(Locale.KOREA, "%,d원", amount);
    }

    /**
     * 서버 ISO-8601 시각 문자열(assigned_at 등)을 기기 시간대 기준으로 파싱한다.
     * 파싱 실패 시 null.
     */
    public static OffsetDateTime parse(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            return OffsetDateTime.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }

    /** "8/20 14:32" 형태의 짧은 시각 표시. 파싱 실패 시 빈 문자열. */
    public static String shortTime(String iso) {
        OffsetDateTime t = parse(iso);
        if (t == null) return "";
        return t.atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("M/d HH:mm", Locale.KOREA));
    }

    /** 해당 시각이 오늘(기기 시간대)인지. */
    public static boolean isToday(String iso) {
        OffsetDateTime t = parse(iso);
        if (t == null) return false;
        LocalDate d = t.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
        return d.equals(LocalDate.now());
    }
}
