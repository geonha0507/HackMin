package com.hackmin.connect.security;

import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * dex(자바) 레벨 루팅 탐지.
 *
 * <p>네이티브(.so) 없이 앱 코드 안에서 흔한 루팅 흔적을 확인한다:
 * <ul>
 *   <li>test-keys 빌드 태그(비공식/커스텀 롬)</li>
 *   <li>su 바이너리·busybox 존재</li>
 *   <li>Superuser/SuperSU 등 루팅 관리앱 흔적</li>
 *   <li>Magisk/KernelSU 경로</li>
 *   <li>{@code which su} 실행 가능 여부</li>
 * </ul>
 *
 * <p>정적 분석(JADX)에는 그대로 보이므로, 실습에서는 Frida 등으로 이 판정을
 * 후킹해 우회하는 것을 목표로 한다(=루팅 우회 실습 표적).
 */
public final class RootDetector {

    private RootDetector() {}

    private static final String[] SU_PATHS = {
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/system/sd/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
            "/data/local/su", "/system/bin/failsafe/su", "/system/xbin/busybox",
    };
    private static final String[] ROOT_APKS = {
            "/system/app/Superuser.apk", "/system/app/SuperSU.apk",
    };
    private static final String[] MAGISK_PATHS = {
            "/sbin/.magisk", "/data/adb/magisk", "/data/adb/modules",
            "/data/adb/ksu", "/cache/.disable_magisk",
    };

    /** 하나라도 걸리면 루팅으로 판정. */
    public static boolean isRooted() {
        return hasTestKeys() || anyExists(SU_PATHS) || anyExists(ROOT_APKS)
                || anyExists(MAGISK_PATHS) || canExecSu();
    }

    private static boolean hasTestKeys() {
        String tags = Build.TAGS;
        return tags != null && tags.contains("test-keys");
    }

    private static boolean anyExists(String[] paths) {
        for (String p : paths) {
            try {
                if (new File(p).exists()) {
                    return true;
                }
            } catch (SecurityException ignored) {
                // 접근 제한이면 판단 불가 — 다음 경로로.
            }
        }
        return false;
    }

    private static boolean canExecSu() {
        Process proc = null;
        try {
            proc = Runtime.getRuntime().exec(new String[]{"which", "su"});
            BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            return r.readLine() != null;
        } catch (Exception e) {
            return false;
        } finally {
            if (proc != null) {
                proc.destroy();
            }
        }
    }
}
