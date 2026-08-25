// [방어 ⑩] 수령확인 서명 — 네이티브 진입점(libhackminsec.so).
//
// 실제 서명 연산은 Java ReceiptSigner.keystoreSign() 이 Android Keystore(EC P-256)
// 하드웨어 키로 수행한다. 개인키는 TEE 밖으로 나오지 않으므로 오프라인 위조가 불가능하다.
// 이 네이티브 함수는 '서명할 데이터(canonical)'를 받아 그 Keystore 서명을 호출하는
// 진입점이자 후킹 타겟이다.
//
//  - 커스텀 클라이언트(무루팅): 개인키가 없어 유효 서명 불가 → 서버 401.
//  - 유일한 우회: 루팅 기기에서 이 함수를 Frida 로 후킹해 canonical(서명 대상)을
//    공격자 요청으로 바꿔치기 → 진짜 Keystore 서명이 공격자 데이터에 찍힌다(서명 오라클).
//    (JNIEXPORT 심볼은 .dynsym 에 남지만 내부 로직은 네이티브라 Ghidra 로 분석해야 한다.)
#include <jni.h>

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_hackmin_app_security_ReceiptSigner_signReceipt(
        JNIEnv *env, jclass clazz, jbyteArray canonical) {
    // Keystore 서명(Java)을 JNI 로 호출한다: byte[] keystoreSign(byte[])
    jmethodID mid = env->GetStaticMethodID(clazz, "keystoreSign", "([B)[B");
    if (mid == nullptr) {
        return nullptr;   // 못 찾으면 JNI 가 NoSuchMethodError 를 걸어둔다
    }
    return (jbyteArray) env->CallStaticObjectMethod(clazz, mid, canonical);
}
