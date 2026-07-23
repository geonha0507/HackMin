package com.hackmin.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 주문내역 "확인함" 상태를 기기 로컬에 저장한다.
 * 주문내역 탭을 열면 현재 주문들을 모두 확인함으로 기록해 뱃지를 없앤다.
 * 이후 새 주문이 생기면 그 주문만 미확인으로 잡혀 다시 뱃지가 표시된다.
 */
public final class OrderSeenStore {

    private static final String PREF = "order_seen";
    private static final String KEY = "seen_ids";

    private OrderSeenStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 확인한 주문 id 집합(문자열). 반환값은 복사본이라 수정해도 안전하다. */
    public static Set<String> getSeenIds(Context c) {
        return new HashSet<>(prefs(c).getStringSet(KEY, new HashSet<>()));
    }

    /** 주어진 주문 id들을 모두 확인함으로 기록한다. */
    public static void markAllSeen(Context c, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        Set<String> seen = getSeenIds(c);
        boolean changed = false;
        for (Long id : ids) {
            if (id != null && seen.add(String.valueOf(id))) {
                changed = true;
            }
        }
        if (changed) {
            prefs(c).edit().putStringSet(KEY, seen).apply();
        }
    }
}
