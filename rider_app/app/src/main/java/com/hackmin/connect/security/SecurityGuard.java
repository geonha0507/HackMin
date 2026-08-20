package com.hackmin.connect.security;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.hackmin.connect.BuildConfig;

/**
 * 앱 실행 시 루팅 여부를 검사하는 가드.
 *
 * <p>루팅이 감지되면 "루팅된 기기에서는 실행할 수 없습니다" 토스트를 띄운 뒤
 * 앱을 종료한다(message 모드). 배달앱(네이티브 가드)과 대칭으로, 라이더 앱은
 * dex 레벨에서 동일한 방어를 둔다.
 *
 * <p><b>dev 토글</b>: {@code BuildConfig.ROOT_GUARD} 가 false 면 검사를 건너뛴다.
 * gitignore 된 gradle.properties 에 {@code rootGuard=false} 를 넣어 개발/에뮬
 * 빌드에서 끈다(LDPlayer 같은 루팅 에뮬에서 테스트 가능). 실습용 빌드는
 * {@code -ProotGuard=true}(기본값)로 켠다.
 */
public final class SecurityGuard {

    private SecurityGuard() {}

    /**
     * 루팅이면 종료 절차를 시작하고 true 를 반환한다(호출부는 즉시 return 할 것).
     * 루팅이 아니거나 가드가 꺼져 있으면 false.
     */
    public static boolean enforce(Activity activity) {
        if (!BuildConfig.ROOT_GUARD) {
            return false;
        }
        if (!RootDetector.isRooted()) {
            return false;
        }
        Toast.makeText(activity,
                "루팅된 기기에서는 실행할 수 없습니다.", Toast.LENGTH_LONG).show();
        activity.finishAffinity();
        // 토스트가 보이도록 잠시 뒤 프로세스 종료.
        new Handler(Looper.getMainLooper())
                .postDelayed(() -> Runtime.getRuntime().exit(0), 1500);
        return true;
    }
}
