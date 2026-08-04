package com.hackmin.app.security;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * 네이티브 보안 가드 로더 (훈련용).
 *
 * <p>네이티브 라이브러리({@code libhackminsec})가 로드될 때 루팅·Frida 흔적을 검사한다.
 * 탐지되면 네이티브가 안내 메시지를 띄운 뒤(JNI 업콜) 스스로 프로세스를 종료한다.</p>
 *
 * <p>Java 계층에는 "루팅됨?"을 판단하는 boolean 메서드나 {@code if(탐지){차단}} 분기가 없다.
 * 따라서 Frida로 Java 메서드를 후킹해 우회하는 방식은 통하지 않는다. 아래
 * {@link #notifyTamper()}는 <b>안내 표시 전용</b>이며, 이를 후킹해 무력화해도 종료는
 * 네이티브가 그대로 수행한다(메시지만 사라질 뿐 차단은 유지). 실제 우회하려면 strip 된
 * {@code .so}를 IDA로 분석해 네이티브 판정 함수를 주소 후킹해야 한다.</p>
 */
public final class SecurityGuard {

    private SecurityGuard() {}

    /** 안내 토스트 표시에 사용할 애플리케이션 컨텍스트. */
    private static volatile Context appContext;

    static {
        // 로드 자체가 네이티브 가드를 발동시킨다.
        System.loadLibrary("hackminsec");
    }

    /**
     * 보안 가드를 활성화한다. 컨텍스트를 보관해 두어, 네이티브가 탐지 시 안내 메시지를
     * 띄울 수 있게 한다. 앱 진입 시 한 번 호출한다.
     */
    public static void init(Context ctx) {
        if (ctx != null) {
            appContext = ctx.getApplicationContext();
        }
    }

    /**
     * 네이티브 가드가 위협 탐지 시 호출한다(백그라운드 스레드 → 메인 스레드 토스트).
     * 표시 전용이며, 실제 종료는 네이티브가 수행한다.
     */
    static void notifyTamper() {
        final Context c = appContext;
        if (c == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(c, "루팅이 감지되어 앱을 종료합니다.", Toast.LENGTH_LONG).show();
            }
        });
    }
}
