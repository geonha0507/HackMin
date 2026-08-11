package com.hackmin.app.util;

/**
 * 1:1 문의 입력 클라이언트 측 XSS 새니타이저("입력 검문소").
 *
 * <p>입력의 HTML 특수문자를 엔티티로 <b>치환(escape)</b>해서 돌려준다(차단 메시지 없음).
 * 예: {@code <img onerror=...>} → {@code &lt;img onerror=...&gt;}. 정상 사용자의 문의는
 * 치환된 채로 저장되어 스크립트로 실행되지 않는다. 실제 치환 로직은
 * <b>네이티브(libhackminfilter)</b>가 수행한다.</p>
 *
 * <p>네이티브 치환 함수는 hidden 심볼로 release 에서 strip 되어 {@code .so} 에서 이름이
 * 지워지므로, 이 필터를 우회하려면 <b>Ghidra 로 치환 함수를 특정한 뒤 Frida 로 "원본을
 * 그대로 반환"하도록 후킹</b>하거나 정적 패치해야 한다. 정상 입력으로는 원본 페이로드를
 * 저장시킬 수 없다.</p>
 *
 * <p>네이티브 로드에 실패한 환경에서만 Java 정규식으로 폴백한다.</p>
 */
public final class XssInputGuard {

    private XssInputGuard() {}

    private static final boolean NATIVE_OK;

    static {
        boolean ok;
        try {
            System.loadLibrary("hackminfilter");
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        NATIVE_OK = ok;
    }

    /** 네이티브 새니타이즈 — 위험 마크업을 제거한 문자열을 돌려준다. 로직은 libhackminfilter(.so)에 있다. */
    private static native String nativeSanitize(String input);

    /**
     * 입력에서 XSS 로 악용될 수 있는 마크업/스크립트를 제거한 문자열을 반환한다.
     *
     * @param input 사용자 입력(문의 제목/내용 등)
     * @return 위험 마크업이 제거된 안전한 문자열(입력이 null 이면 그대로 반환)
     */
    public static String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        if (NATIVE_OK) {
            String result = nativeSanitize(input);
            return result != null ? result : input;
        }
        // 폴백: HTML 특수문자를 엔티티로 치환(escape) — OWASP 6개 세트.
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("/", "&#x2F;");
    }
}
