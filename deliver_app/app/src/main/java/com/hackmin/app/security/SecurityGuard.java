package com.hackmin.app.security;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * 네이티브 보안 가드 로더 (훈련용).
 *
 * <p>이 클래스는 네이티브 라이브러리({@code libhackminsec}) 로드와, (message 빌드에서만) 네이티브가
 * 올려주는 안내 토스트 표시를 담당한다. 루팅·Frida <b>판정과 종료는 전부 네이티브</b>가 하며,
 * Java 에는 판정 분기가 없다.</p>
 *
 * <p>종료 방식은 빌드 플래그로 갈린다(app/build.gradle.kts 의 -PguardMode):</p>
 * <ul>
 *   <li><b>inline</b>(기본): 로드 순간 네이티브가 동기·인라인으로 즉시 종료. 콜백/스레드 없음 →
 *       {@code notifyTamper} 는 호출되지 않는다. 우회하려면 strip 된 {@code .so} 를 Ghidra 로
 *       분석해 판정 함수를 패치해야 한다.</li>
 *   <li><b>message</b>: 네이티브 백그라운드 스레드가 탐지 후 {@link #notifyTamper(int)} 로 토스트를
 *       띄우고 종료.</li>
 * </ul>
 */
public final class SecurityGuard {

    private static Context appContext;

    private SecurityGuard() {}

    static {
        // 로드 자체가 네이티브 가드를 발동시킨다(탐지 시 네이티브가 프로세스를 종료).
        System.loadLibrary("hackminsec");
    }

    /**
     * 가드 발동 + (message 빌드용) 토스트 컨텍스트 등록.
     * 이 메서드를 호출(클래스 로드)하는 것만으로 위 static 초기화가 실행되어 가드가 동작한다.
     */
    public static void init(Context ctx) {
        if (ctx != null) {
            appContext = ctx.getApplicationContext();
        }
    }

    /**
     * 네이티브(message 빌드)가 탐지 시 호출하는 안내 콜백. 메인 스레드에서 토스트를 띄운다.
     * inline 빌드에서는 호출되지 않는다.
     *
     * @param code 0 = 루팅, 1 = 비정상 환경(Frida 등)
     */
    @SuppressWarnings("unused") // 네이티브에서 JNI 로 호출
    public static void notifyTamper(int code) {
        final Context ctx = appContext;
        if (ctx == null) return;
        final String msg = (code == 0)
                ? "루팅이 감지되어 앱을 종료합니다."
                : "비정상 실행 환경이 감지되어 앱을 종료합니다.";
        new Handler(Looper.getMainLooper()).post(
                () -> Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show());
    }
}
