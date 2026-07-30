package com.hackmin.app.ui.common;

import android.os.SystemClock;

/**
 * 연타(빠른 연속 클릭) 방지용 전역 디바운스 가드.
 *
 * <p>버튼/메뉴/목록 항목을 빠르게 여러 번 누르면 화면이나 다이얼로그가 중복으로
 * 열리는 문제를 막는다. {@link #allow()}가 마지막 허용 시점부터 {@link #WINDOW_MS}
 * 이내면 {@code false}를 돌려주어 두 번째 이후 클릭을 무시한다.</p>
 *
 * <p>전역(static) 타임스탬프를 쓰므로, 서로 다른 화면 전환이 거의 동시에 일어나는
 * 경우까지 한 번만 처리된다. 네비게이션은 {@link BaseActivity}가 startActivity에서
 * 자동으로 이 가드를 태우고, 다이얼로그·즉시 실행 동작은 각 클릭에서 직접 호출한다.</p>
 */
public final class ClickGuard {

    private ClickGuard() {}

    /** 이 시간(ms) 이내의 연속 클릭은 무시한다. */
    private static final long WINDOW_MS = 600L;

    private static long lastAllowedAt = 0L;

    /**
     * 지금 클릭을 처리해도 되는지 여부.
     *
     * @return 직전 허용 이후 {@link #WINDOW_MS}가 지났으면 true(처리 진행),
     *         아직 이내면 false(연타로 간주하여 무시)
     */
    public static boolean allow() {
        long now = SystemClock.uptimeMillis();
        if (now - lastAllowedAt < WINDOW_MS) {
            return false;
        }
        lastAllowedAt = now;
        return true;
    }
}
