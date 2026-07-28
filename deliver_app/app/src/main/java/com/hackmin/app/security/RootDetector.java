package com.hackmin.app.security;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * 루팅 탐지 유틸리티 (훈련용).
 *
 * <p>모의해킹 실습 대상 — 여러 표준 루팅 지표를 검사한다. 실제 상용 앱(쿠팡이츠 등)이
 * 쓰는 방식과 동일하게 su 바이너리·루트 관리앱·빌드태그·마운트 상태 등을 확인한다.</p>
 *
 * <p><b>[Frida 우회 실습]</b> 최종 판정은 {@link #isDeviceRooted(Context)} 하나로 모인다.
 * 이 메서드(또는 하위 check* 메서드)를 Frida로 후킹해 항상 {@code false}를 반환시키면
 * 루팅 탐지를 우회할 수 있다. 예:
 * <pre>
 * Java.perform(function () {
 *   var R = Java.use("com.hackmin.app.security.RootDetector");
 *   R.isDeviceRooted.overload('android.content.Context').implementation = function (ctx) {
 *     return false;   // 항상 정상 기기로 위장
 *   };
 * });
 * </pre>
 *
 * <p><b>조치방안(참고):</b> 단일 boolean 게이트는 후킹 한 방에 뚫린다. 실제 방어는
 * 검사 분산·서버측 무결성 검증(SafetyNet/Play Integrity)·네이티브 검사 등을 병행해야 한다.</p>
 */
public final class RootDetector {

    private RootDetector() {}

    /** su 바이너리가 위치할 수 있는 대표 경로들. */
    private static final String[] SU_PATHS = {
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/su",
            "/system/bin/.ext/.su",
            "/system/usr/we-need-root/su-backup",
            "/system/xbin/mu",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/su/bin/su",
            "/vendor/bin/su"
    };

    /** 루트 관리·클로킹 앱 패키지명. */
    private static final String[] ROOT_PACKAGES = {
            "com.topjohnwu.magisk",          // Magisk
            "eu.chainfire.supersu",          // SuperSU
            "com.noshufou.android.su",       // Superuser
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.ramdroid.appquarantine",    // 루트 클로킹
            "com.devadvance.rootcloak",
            "com.devadvance.rootcloakplus",
            "de.robv.android.xposed.installer"
    };

    /**
     * 기기가 루팅되었는지 종합 판정한다.
     *
     * @return 루팅 지표가 하나라도 발견되면 true
     */
    public static boolean isDeviceRooted(Context context) {
        return checkSuBinary()
                || checkRootPackages(context)
                || checkTestKeys()
                || checkSuCommand()
                || checkRootDirs();
    }

    /** su 바이너리 파일 존재 여부. */
    private static boolean checkSuBinary() {
        for (String path : SU_PATHS) {
            if (new File(path).exists()) {
                return true;
            }
        }
        return false;
    }

    /** 설치된 루트 관리앱 패키지 확인. */
    private static boolean checkRootPackages(Context context) {
        if (context == null) return false;
        PackageManager pm = context.getPackageManager();
        for (String pkg : ROOT_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                return true;   // 설치되어 있음
            } catch (PackageManager.NameNotFoundException ignored) {
                // 미설치 — 정상
            }
        }
        return false;
    }

    /** 커스텀/개발 ROM 흔적(test-keys) 확인. */
    private static boolean checkTestKeys() {
        String tags = Build.TAGS;
        return tags != null && tags.contains("test-keys");
    }

    /** `which su` 실행으로 su 명령 존재 여부 확인. */
    private static boolean checkSuCommand() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"which", "su"});
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = in.readLine();
            return line != null && !line.isEmpty();
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /** 루팅 시 흔히 생기는 디렉터리/파일 존재 확인. */
    private static boolean checkRootDirs() {
        String[] paths = {
                "/system/app/Superuser.apk",
                "/data/adb/magisk",
                "/data/adb/modules",
                "/sbin/.magisk",
                "/cache/.disable_magisk"
        };
        for (String path : paths) {
            if (new File(path).exists()) {
                return true;
            }
        }
        return false;
    }
}
