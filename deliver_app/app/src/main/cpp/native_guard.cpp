// 네이티브 보안 가드 (훈련용) — 루팅 · Frida 탐지 및 차단.
//
// 설계 목표:
//  - 판정·차단을 전부 네이티브에서 수행하고, 탐지 시 native가 직접 프로세스를 종료한다
//    (_exit). Java 쪽에는 후킹 가능한 "if(탐지){...}" 분기나 boolean 반환이 없다.
//    → Frida의 Java 후킹(예: isDeviceRooted/isFridaDetected → false)만으로는 우회 불가.
//  - 실제 판정 로직은 전부 내보내지 않는(hidden) static 함수에 두고, dlopen 시점의
//    이름 없는 constructor(.init_array)와 JNI_OnLoad에서 호출한다. release strip 후
//    .so를 열면 이 함수들이 sub_XXXX 로만 보여, IDA/Ghidra로 offset을 직접 찾아
//    Frida 주소 후킹을 해야만 우회할 수 있다.
//  - libc의 access()/open()/read()/socket() 를 이름으로 후킹당하지 않도록, 파일 존재·
//    내용·소켓 검사를 가능한 한 raw syscall 로 수행한다. (libc 결과 하나에만 의존하지
//    않도록 프로퍼티 API + build.prop 파일 읽기 등 방식을 병행한다.)
//
// 주의: 무결성/서명 검사는 범위 밖(넣지 않음). SecureStore 등 다른 보안 모듈은 건드리지 않음.
//       정상 기기(탐지 지표 없음)에서는 절대 종료하지 않는다.

#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <string.h>
#include <stdint.h>
#include <stdio.h>
#include <pthread.h>
#include <sys/syscall.h>
#include <sys/system_properties.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#define HIDDEN __attribute__((visibility("hidden")))

namespace {

// ───────────────────────── raw syscall 래퍼 ─────────────────────────
// libc 심볼(access/open/read/socket/connect)을 직접 이름 후킹하는 것을 피하기 위해
// 시스템 콜을 직접 호출한다. (syscall() 자체를 후킹하려면 번호·인자 필터가 필요해 난이도↑)

HIDDEN long sc_faccess(const char *path) {
    return syscall(SYS_faccessat, AT_FDCWD, path, F_OK, 0);
}

HIDDEN int sc_open(const char *path) {
    return (int) syscall(SYS_openat, AT_FDCWD, path, O_RDONLY | O_CLOEXEC, 0);
}

HIDDEN long sc_read(int fd, void *buf, size_t n) {
    return syscall(SYS_read, fd, buf, n);
}

HIDDEN void sc_close(int fd) {
    syscall(SYS_close, fd);
}

// 파일 내용을 청크 단위로 읽어 needle 중 하나라도 포함하면 true.
HIDDEN bool file_contains_any(const char *path, const char *const *needles, int count) {
    int fd = sc_open(path);
    if (fd < 0) return false;
    char buf[4096];
    long r;
    while ((r = sc_read(fd, buf, sizeof(buf) - 1)) > 0) {
        buf[r] = '\0';
        for (int i = 0; i < count; i++) {
            if (strstr(buf, needles[i]) != nullptr) {
                sc_close(fd);
                return true;
            }
        }
    }
    sc_close(fd);
    return false;
}

// ───────────────────────── 루팅 탐지 ─────────────────────────

HIDDEN const char *const ROOT_PATHS[] = {
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/su",
        "/system/bin/.ext/.su", "/system/usr/we-need-root/su-backup",
        "/system/xbin/mu", "/data/local/xbin/su", "/data/local/bin/su",
        "/data/local/su", "/su/bin/su", "/vendor/bin/su",
        "/system/app/Superuser.apk",
        "/data/adb/magisk", "/data/adb/modules", "/sbin/.magisk",
        // 참고: frida-server "파일" 존재는 휴면 상태(미실행)에서도 걸려 테스트를 막으므로
        // 여기 넣지 않는다. 실제 동작 중인 Frida는 아래 maps/포트/스레드 검사로 탐지한다.
};

HIDDEN bool detect_root_paths() {
    const int n = (int) (sizeof(ROOT_PATHS) / sizeof(ROOT_PATHS[0]));
    for (int i = 0; i < n; i++) {
        if (sc_faccess(ROOT_PATHS[i]) == 0) return true;
    }
    return false;
}

// 빌드 태그 test-keys: 프로퍼티 API + build.prop 파일 병행(한쪽이 후킹돼도 교차검증).
HIDDEN bool detect_test_keys() {
    char tags[PROP_VALUE_MAX] = {0};
    if (__system_property_get("ro.build.tags", tags) > 0 && strstr(tags, "test-keys")) {
        return true;
    }
    const char *nk[] = {"test-keys"};
    if (file_contains_any("/system/build.prop", nk, 1)) return true;
    return false;
}

// ───────────────────────── Frida 탐지 ─────────────────────────

// 로드된 라이브러리 매핑에 Frida 흔적이 있는지(/proc/self/maps).
HIDDEN bool detect_frida_maps() {
    const char *needles[] = {
            "frida", "frida-agent", "frida-gadget", "libfrida",
            "gum-js-loop", "re.frida.server",
    };
    return file_contains_any("/proc/self/maps", needles, 6);
}

// Frida 기본 포트(27042/27043) 리스너 존재 여부 — raw socket/connect.
HIDDEN bool detect_frida_port() {
    const int ports[] = {27042, 27043};
    for (int i = 0; i < 2; i++) {
        int fd = (int) syscall(SYS_socket, AF_INET, SOCK_STREAM, 0);
        if (fd < 0) continue;
        struct sockaddr_in addr;
        memset(&addr, 0, sizeof(addr));
        addr.sin_family = AF_INET;
        addr.sin_port = htons((uint16_t) ports[i]);
        addr.sin_addr.s_addr = htonl(0x7F000001);  // 127.0.0.1
        long rc = syscall(SYS_connect, fd, (struct sockaddr *) &addr, sizeof(addr));
        sc_close(fd);
        if (rc == 0) return true;  // 연결 성공 → 리스너 존재
    }
    return false;
}

struct linux_dirent64 {
    uint64_t d_ino;
    int64_t d_off;
    unsigned short d_reclen;
    unsigned char d_type;
    char d_name[];
};

// 주입 스레드 이름 검사(/proc/self/task/<tid>/comm) — Frida 전용 명칭만 사용(오탐 방지).
HIDDEN bool detect_frida_threads() {
    int fd = sc_open("/proc/self/task");
    if (fd < 0) return false;
    // Frida 전용 스레드명만 사용(gmain/gdbus 등은 GLib 계열에서도 나올 수 있어 오탐 방지 위해 제외).
    const char *needles[] = {"gum-js-loop", "pool-frida"};
    const int nn = 2;
    char dbuf[4096];
    long n;
    while ((n = syscall(SYS_getdents64, fd, dbuf, sizeof(dbuf))) > 0) {
        long off = 0;
        while (off < n) {
            auto *de = (linux_dirent64 *) (dbuf + off);
            if (de->d_name[0] != '.') {
                char commPath[288];
                snprintf(commPath, sizeof(commPath), "/proc/self/task/%s/comm", de->d_name);
                char name[64] = {0};
                int cfd = sc_open(commPath);
                if (cfd >= 0) {
                    long r = sc_read(cfd, name, sizeof(name) - 1);
                    sc_close(cfd);
                    if (r > 0) {
                        name[r] = '\0';
                        for (int i = 0; i < nn; i++) {
                            if (strstr(name, needles[i]) != nullptr) {
                                sc_close(fd);
                                return true;
                            }
                        }
                    }
                }
            }
            off += de->d_reclen;
        }
    }
    sc_close(fd);
    return false;
}

// ───────────────────────── 종합 판정 + 차단 ─────────────────────────

HIDDEN bool is_compromised() {
    return detect_root_paths()
           || detect_test_keys()
           || detect_frida_maps()
           || detect_frida_port()
           || detect_frida_threads();
}

// JNI 업콜용(안내 토스트 표시에만 사용). 실제 판정·종료는 네이티브가 수행.
// 앱 클래스/메서드는 JNI_OnLoad(메인 스레드=앱 클래스로더)에서 미리 캐시한다.
// (네이티브 스레드에서 FindClass 하면 시스템 클래스로더라 앱 클래스를 못 찾는 함정 회피.)
HIDDEN JavaVM *g_vm = nullptr;
HIDDEN jclass g_guardClass = nullptr;      // GlobalRef
HIDDEN jmethodID g_notifyMethod = nullptr;

// 탐지 시: 안내 메시지를 띄우고 잠시 후 프로세스를 종료하는 킬러 스레드.
// (Java notifyTamper를 후킹해 무력화해도 아래 _exit로 종료는 그대로 수행된다.)
HIDDEN void *killer_thread(void *arg) {
    (void) arg;
    // init(ctx)가 컨텍스트를 채우고 첫 화면이 뜰 시간을 잠깐 준다(그 뒤 안내 표시).
    usleep(400 * 1000);
    JNIEnv *env = nullptr;
    if (g_vm != nullptr && g_guardClass != nullptr && g_notifyMethod != nullptr
        && g_vm->AttachCurrentThread(&env, nullptr) == JNI_OK && env != nullptr) {
        env->CallStaticVoidMethod(g_guardClass, g_notifyMethod);  // 캐시된 전역 참조 사용
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        g_vm->DetachCurrentThread();
    }
    usleep(1800 * 1000);  // 토스트가 보일 시간 확보 후 종료
    _exit(0);
}

// 탐지 시에만 발동. 정상 기기에서는 아무 일도 하지 않는다(오탐으로 앱을 죽이지 않음).
HIDDEN void enforce() {
    if (is_compromised()) {
        pthread_t t;
        if (pthread_create(&t, nullptr, killer_thread, nullptr) == 0) {
            pthread_detach(t);
        } else {
            _exit(0);  // 스레드 생성 실패 시엔 안내 없이 즉시 종료(차단 우선).
        }
    }
}

}  // namespace

// 런타임이 라이브러리 로드 시 호출하는 유일한 export 심볼. JavaVM을 확보하고 판정을 발동한다.
// (실제 판정 로직은 위의 hidden static 함수들에 있어, strip 후 IDA로 offset을 찾아야 후킹 가능.)
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;
    g_vm = vm;
    // 메인 스레드(앱 클래스로더 컨텍스트)에서 안내 메서드를 미리 캐시해 둔다.
    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) == JNI_OK && env != nullptr) {
        jclass local = env->FindClass("com/hackmin/app/security/SecurityGuard");
        if (local != nullptr) {
            g_guardClass = (jclass) env->NewGlobalRef(local);
            g_notifyMethod = env->GetStaticMethodID(g_guardClass, "notifyTamper", "()V");
            env->DeleteLocalRef(local);
        }
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
    }
    enforce();
    return JNI_VERSION_1_6;
}
