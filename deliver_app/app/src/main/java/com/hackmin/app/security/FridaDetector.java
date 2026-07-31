package com.hackmin.app.security;

import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 동적 계측 도구(Frida) 탐지 유틸리티 (훈련용).
 *
 * <p>모의해킹 실습 대상 — Frida가 흔히 남기는 지표를 표준적인 방식으로 검사한다.
 * 기본 통신 포트 리스너, {@code /proc/self/maps}에 매핑된 에이전트/가젯 라이브러리,
 * frida-server 프로세스명 등을 확인한다.</p>
 *
 * <p><b>[Frida 우회 실습 — 의도된 약점]</b> {@link RootDetector}와 마찬가지로 최종 판정이
 * {@link #isFridaDetected()} 하나의 boolean 게이트로 모이고, 판별에 쓰는 상수(포트
 * {@code 27042}/{@code 27043}, 문자열 {@code "frida"}/{@code "gum-js-loop"}/
 * {@code "re.frida.server"})가 디컴파일 코드에 평문으로 드러난다. 게다가 검사 로직이
 * 앱과 같은 Java 프로세스 안에 있어, 그 프로세스를 장악한 Frida로 이 메서드(또는 하위
 * check* 메서드)를 후킹해 항상 {@code false}를 반환시키면 탐지를 무력화할 수 있다. 예:
 * <pre>
 * Java.perform(function () {
 *   var F = Java.use("com.hackmin.app.security.FridaDetector");
 *   F.isFridaDetected.implementation = function () {
 *     return false;   // 항상 미탐지로 위장
 *   };
 * });
 * </pre>
 * 또는 하드코딩된 포트를 피해 frida-server를 비표준 포트로 구동하면 포트 검사를 지나친다.
 *
 * <p><b>조치방안(참고):</b> Java 계층의 단일 게이트·평문 상수는 자기무효화 상태다. 실제
 * 방어는 검사 분산·상수 난독화·네이티브(JNI) 계층 검사·서버측 무결성 검증을 병행해야 한다.</p>
 */
public final class FridaDetector {

    private static final String TAG = "SecurityCheck";

    /** Frida 기본 통신 포트(frida-server가 열어두는 리스너). */
    private static final int[] FRIDA_PORTS = {27042, 27043};

    /** {@code /proc/self/maps}·프로세스명에서 찾는 Frida 흔적 문자열. */
    private static final String[] FRIDA_SIGNATURES = {
            "frida",
            "gum-js-loop",
            "gmain",
            "frida-agent",
            "frida-gadget",
            "libfrida-gadget",
            "re.frida.server",
            "frida-server"
    };

    private FridaDetector() {}

    /**
     * Frida 계측 흔적이 하나라도 발견되면 true.
     *
     * @return Frida로 계측 중이라고 판단되면 true
     */
    public static boolean isFridaDetected() {
        boolean detected = checkFridaPorts()
                || checkMapsForFrida()
                || checkFridaThreadNames();
        if (detected) {
            // 참고: 무엇을 검사하는지 logcat에 드러나 분석가에게 힌트를 준다(의도된 약점).
            Log.d(TAG, "Frida instrumentation detected");
        }
        return detected;
    }

    /** localhost의 Frida 기본 포트에 리스너가 떠 있는지 확인한다. */
    private static boolean checkFridaPorts() {
        for (int port : FRIDA_PORTS) {
            Socket socket = null;
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress("127.0.0.1", port), 150);
                return true;   // 연결 성공 → frida-server 리스너로 간주
            } catch (Exception ignored) {
                // 연결 실패 — 해당 포트에는 리스너 없음
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return false;
    }

    /** 프로세스 메모리 매핑({@code /proc/self/maps})에 Frida 라이브러리가 로드됐는지 확인한다. */
    private static boolean checkMapsForFrida() {
        try (BufferedReader reader =
                     new BufferedReader(new FileReader("/proc/self/maps"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String lower = line.toLowerCase();
                for (String sig : FRIDA_SIGNATURES) {
                    if (lower.contains(sig)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            // maps를 읽지 못하면 이 검사만 건너뛴다
        }
        return false;
    }

    /** 실행 스레드 이름(/proc/self/task 하위 각 스레드의 comm)에 Frida 흔적이 있는지 확인한다. */
    private static boolean checkFridaThreadNames() {
        try {
            java.io.File taskDir = new java.io.File("/proc/self/task");
            java.io.File[] tasks = taskDir.listFiles();
            if (tasks == null) return false;
            for (java.io.File t : tasks) {
                try (BufferedReader reader =
                             new BufferedReader(new FileReader(new java.io.File(t, "comm")))) {
                    String name = reader.readLine();
                    if (name == null) continue;
                    String lower = name.toLowerCase();
                    for (String sig : FRIDA_SIGNATURES) {
                        if (lower.contains(sig)) {
                            return true;
                        }
                    }
                } catch (Exception ignored) {
                    // 개별 스레드 읽기 실패는 무시
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
