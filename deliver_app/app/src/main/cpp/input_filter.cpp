// 1:1 문의 입력 XSS 검문소 — 네이티브 구현 (훈련용).
//
// 입력의 HTML 특수문자를 엔티티로 치환(escape)해서 돌려준다.
//   <  →  &lt;      >  →  &gt;      &  →  &amp;      "  →  &quot;      '  →  &#39;
// 따라서 <img onerror=...> 같은 페이로드는 "&lt;img onerror=...&gt;" 로 저장되어,
// 화면에는 글자로 보이지만 스크립트로 실행되지는 않는다(차단 메시지 없이 치환).
//
// 치환 로직(escape_into)은 hidden 심볼이라 release(--strip-all)에서 이름이 지워져
// .so 에서 sub_XXXX 로만 보인다. JNI 진입점(Java_..._nativeSanitize)만 export 되므로,
// 이 필터를 우회하려면
//   1) Ghidra 로 JNI 진입점 → 내부 치환 로직을 특정하고
//   2) Frida 로 그 함수가 "원본을 그대로 반환"하도록 후킹하거나 정적 패치해야 한다.
// (정상 입력은 치환됨 → 원본 페이로드를 저장하려면 동적 계측(Frida)이 정당해지는 구조.)
//
// 주의: HTML 을 파싱/렌더링하지 않는다. 문자 단위 치환만 한다.

#include <jni.h>

#define HIDDEN __attribute__((visibility("hidden")))

namespace {

// HTML 특수문자를 엔티티로 치환해 out 에 채운다. 버퍼가 차면 안전하게 잘라 끝낸다.
HIDDEN void escape_into(const char *s, char *out, int cap) {
    int o = 0;
    for (int i = 0; s[i] != '\0'; i++) {
        const char *rep = nullptr;
        switch (s[i]) {
            case '&':  rep = "&amp;";   break;
            case '<':  rep = "&lt;";    break;
            case '>':  rep = "&gt;";    break;
            case '"':  rep = "&quot;";  break;
            case '\'': rep = "&#x27;";  break;   // OWASP: 16진수 권장
            case '/':  rep = "&#x2F;";  break;   // 태그 조기 종료 방지
            default:   rep = nullptr;   break;
        }
        if (rep != nullptr) {
            for (int k = 0; rep[k] != '\0'; k++) {
                if (o >= cap - 1) { out[o] = '\0'; return; }
                out[o++] = rep[k];
            }
        } else {
            if (o >= cap - 1) { out[o] = '\0'; return; }
            out[o++] = s[i];
        }
    }
    out[o] = '\0';
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_hackmin_app_util_XssInputGuard_nativeSanitize(JNIEnv *env, jclass clazz, jstring input) {
    (void) clazz;
    if (input == nullptr) return input;
    const char *s = env->GetStringUTFChars(input, nullptr);
    if (s == nullptr) return input;

    char out[16384];
    escape_into(s, out, (int) sizeof(out));

    env->ReleaseStringUTFChars(input, s);
    return env->NewStringUTF(out);
}
