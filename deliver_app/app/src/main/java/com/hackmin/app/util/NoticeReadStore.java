package com.hackmin.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * 공지사항 읽음 상태를 기기 로컬(SharedPreferences)에 저장한다.
 * 서버가 사용자별 읽음 상태를 관리하지 않으므로 클라이언트에서 읽은 공지 id를 기록한다.
 */
public final class NoticeReadStore {

    private static final String PREF = "notice_read";
    private static final String KEY = "read_ids";

    private NoticeReadStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 읽은 공지 id 집합(문자열). 반환값은 복사본이라 수정해도 안전하다. */
    public static Set<String> getReadIds(Context c) {
        return new HashSet<>(prefs(c).getStringSet(KEY, new HashSet<>()));
    }

    public static boolean isRead(Context c, long id) {
        return getReadIds(c).contains(String.valueOf(id));
    }

    /** 해당 공지를 읽음으로 기록한다. */
    public static void markRead(Context c, long id) {
        Set<String> ids = getReadIds(c);
        if (ids.add(String.valueOf(id))) {
            prefs(c).edit().putStringSet(KEY, ids).apply();
        }
    }
}
