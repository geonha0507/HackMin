// 네이티브 보안 가드 (훈련용) — 루팅 · Frida 탐지 및 차단.
//
// 탐지·판정은 전부 네이티브에서 수행한다:
//   - hidden static + raw syscall + 자체 문자열매칭(strstr/access/open/read 미사용) → libc 이름
//     후킹 무력화. release 에서 --strip-all 되어 .so 에서 sub_XXXX 로만 보인다.
//
// 종료(자폭) 방식은 빌드 플래그(GUARD_DEFER_MSG)로 전환한다:
//   [기본] INLINE       — dlopen 시점 constructor 에서 탐지+종료를 동기·인라인으로 수행.
//                         스레드/JNI 콜백 없음. 후킹할 pthread_create/_exit 가 없어 우회하려면
//                         Ghidra/IDA 로 판정 함수 offset 을 찾아 패치해야 한다(가장 강함).
//                         단, 탐지 시 onCreate 도중 즉시 종료라 UI 메시지는 표시 불가.
//   [-PguardMode=message] DEFERRED+MESSAGE — constructor 가 백그라운드 스레드만 띄우고 즉시 반환.
//                         그 스레드가 로그인 화면이 뜬 뒤 탐지 → "루팅 감지" 토스트 → 종료.
//                         UX 는 깔끔하나 pthread_create/Java 후킹으로 우회 가능(방어 약화).
//
// 주의: 무결성/서명 검사는 범위 밖. SecureStore 등 다른 보안 모듈은 건드리지 않는다.
//       정상 기기(탐지 지표 없음)에서는 절대 종료하지 않는다.

#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdint.h>
#include <sys/syscall.h>
#include <sys/system_properties.h>
#ifdef GUARD_DEFER_MSG
#include <pthread.h>
#endif

#define HIDDEN __attribute__((visibility("hidden")))

namespace {

// ───────────────────────── raw syscall (인라인 svc, libc 미경유) ─────────────────────────
//
// libc 의 syscall() 래퍼를 쓰면 그 함수 하나를 후킹당해 번호별로 가로채일 수 있다.
// 그래서 svc #0(arm64)/syscall(x86_64) 명령을 인라인 어셈블리로 직접 발행해 libc 를
// 아예 거치지 않는다 → .so 에 syscall@LIBC 임포트가 없어지고, 후킹할 라이브러리 경계가 없다.

#if defined(__aarch64__)
HIDDEN inline long raw_syscall(long n, long a0, long a1, long a2, long a3) {
    register long x8 asm("x8") = n;
    register long x0 asm("x0") = a0;
    register long x1 asm("x1") = a1;
    register long x2 asm("x2") = a2;
    register long x3 asm("x3") = a3;
    asm volatile("svc #0" : "+r"(x0) : "r"(x8), "r"(x1), "r"(x2), "r"(x3) : "memory", "cc");
    return x0;
}
#elif defined(__x86_64__)
HIDDEN inline long raw_syscall(long n, long a0, long a1, long a2, long a3) {
    long ret;
    register long r10 asm("r10") = a3;
    asm volatile("syscall"
                 : "=a"(ret)
                 : "a"(n), "D"(a0), "S"(a1), "d"(a2), "r"(r10)
                 : "rcx", "r11", "memory");
    return ret;
}
#else
HIDDEN inline long raw_syscall(long n, long a0, long a1, long a2, long a3) {
    return syscall(n, a0, a1, a2, a3);   // 그 외 ABI 폴백
}
#endif

HIDDEN long sc_faccess(const char *path) {
    return raw_syscall(SYS_faccessat, AT_FDCWD, (long) path, F_OK, 0);
}

HIDDEN int sc_open(const char *path) {
    return (int) raw_syscall(SYS_openat, AT_FDCWD, (long) path, O_RDONLY | O_CLOEXEC, 0);
}

HIDDEN long sc_read(int fd, void *buf, unsigned long n) {
    return raw_syscall(SYS_read, fd, (long) buf, (long) n, 0);
}

HIDDEN void sc_close(int fd) {
    raw_syscall(SYS_close, fd, 0, 0, 0);
}

HIDDEN void sc_die() {
    raw_syscall(SYS_exit_group, 0, 0, 0, 0);
    raw_syscall(SYS_exit, 0, 0, 0, 0);
}

// strstr 대체(자체 구현) — libc strstr 후킹 무력화.
HIDDEN bool g_contains(const char *hay, const char *needle) {
    if (hay == nullptr || needle == nullptr) return false;
    for (const char *h = hay; *h; h++) {
        const char *a = h;
        const char *b = needle;
        while (*a && *b && *a == *b) { a++; b++; }
        if (*b == '\0') return true;
    }
    return false;
}

// snprintf 대체(자체 구현) — "/proc/self/task/<tid>/comm" 조립. vsnprintf@LIBC 임포트 제거.
HIDDEN void build_comm_path(char *out, const char *tid) {
    char *o = out;
    for (const char *s = "/proc/self/task/"; *s; s++) *o++ = *s;
    for (const char *s = tid; *s; s++) *o++ = *s;
    for (const char *s = "/comm"; *s; s++) *o++ = *s;
    *o = '\0';
}

HIDDEN bool file_contains_any(const char *path, const char *const *needles, int count) {
    int fd = sc_open(path);
    if (fd < 0) return false;
    char buf[4096];
    long r;
    while ((r = sc_read(fd, buf, sizeof(buf) - 1)) > 0) {
        buf[r] = '\0';
        for (int i = 0; i < count; i++) {
            if (g_contains(buf, needles[i])) {
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
};

HIDDEN bool detect_root_paths() {
    const int n = (int) (sizeof(ROOT_PATHS) / sizeof(ROOT_PATHS[0]));
    for (int i = 0; i < n; i++) {
        if (sc_faccess(ROOT_PATHS[i]) == 0) return true;
    }
    return false;
}

HIDDEN bool detect_test_keys() {
    char tags[PROP_VALUE_MAX] = {0};
    if (__system_property_get("ro.build.tags", tags) > 0 && g_contains(tags, "test-keys")) {
        return true;
    }
    const char *nk[] = {"test-keys"};
    if (file_contains_any("/system/build.prop", nk, 1)) return true;
    return false;
}

// ───────────────────────── Frida 탐지 (포트 무관 다중 지표) ─────────────────────────

// 1) /proc/self/maps 의 frida-agent/gum 매핑 — 부착(계측) 시 포트와 무관하게 잡힌다. ← 핵심.
HIDDEN bool detect_frida_maps() {
    const char *needles[] = {
            "frida", "frida-agent", "frida-gadget", "libfrida",
            "gum-js-loop", "libgum", "gadget", "re.frida.server",
    };
    return file_contains_any("/proc/self/maps", needles, 8);
}

struct linux_dirent64 {
    uint64_t d_ino;
    int64_t d_off;
    unsigned short d_reclen;
    unsigned char d_type;
    char d_name[];
};

// 2) 주입 스레드 이름(/proc/self/task/<tid>/comm).
HIDDEN bool detect_frida_threads() {
    int fd = sc_open("/proc/self/task");
    if (fd < 0) return false;
    const char *needles[] = {"gum-js-loop", "gmain", "gdbus", "pool-frida", "pool-spawner"};
    const int nn = 5;
    char dbuf[4096];
    long n;
    while ((n = raw_syscall(SYS_getdents64, fd, (long) dbuf, sizeof(dbuf), 0)) > 0) {
        long off = 0;
        while (off < n) {
            auto *de = (linux_dirent64 *) (dbuf + off);
            if (de->d_reclen == 0) break;   // 방어(무한루프 방지)
            if (de->d_name[0] != '.') {
                char commPath[288];
                build_comm_path(commPath, de->d_name);
                char name[64] = {0};
                int cfd = sc_open(commPath);
                if (cfd >= 0) {
                    long r = sc_read(cfd, name, sizeof(name) - 1);
                    sc_close(cfd);
                    if (r > 0) {
                        name[r] = '\0';
                        for (int i = 0; i < nn; i++) {
                            if (g_contains(name, needles[i])) {
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

// (포트 connect 검사는 제거 — 닫힌 포트에 빠른 RST가 안 오는 환경에서 블록/ANR 유발.
//  실제 위협인 "Frida 부착"은 detect_frida_maps 가 포트와 무관하게 확실히 잡는다.)

HIDDEN bool frida_present() {
    return detect_frida_maps() || detect_frida_threads();
}

// 위협 종류: 0 = 루팅, 1 = Frida, -1 = 정상.
HIDDEN int compromise_reason() {
    if (detect_root_paths() || detect_test_keys()) return 0;
    if (frida_present()) return 1;
    return -1;
}

// ───────────────────────── 종료(자폭) — 빌드 플래그로 전환 ─────────────────────────

#ifdef GUARD_DEFER_MSG
// [DEMO / MESSAGE] 시연용 — 탐지·킬을 생성자(.init_array)가 아니라 JNI_OnLoad(로드 후)에서
// 개시하고, 킬은 pthread_create + libc _exit 로 수행한다. 이 둘은 Frida 후킹 지점이므로
// (find_killer.js 로 _exit/스레드 지점을 찾고 → Ghidra 로 함수 offset 확정 → Interceptor.replace)
// 로 우회 시연이 가능하다. (강한 버전은 inline: 생성자 + raw syscall exit_group.)

HIDDEN JavaVM *g_vm = nullptr;
HIDDEN jclass g_guardClass = nullptr;      // GlobalRef
HIDDEN jmethodID g_notifyMethod = nullptr;

// 안내 토스트(캐시된 참조) 표시 후 libc _exit 로 종료. _exit 는 Frida 로 후킹 가능(시연 지점).
HIDDEN void show_message_and_die(int code) {
    JNIEnv *env = nullptr;
    if (g_vm != nullptr && g_guardClass != nullptr && g_notifyMethod != nullptr
        && g_vm->AttachCurrentThread(&env, nullptr) == JNI_OK && env != nullptr) {
        env->CallStaticVoidMethod(g_guardClass, g_notifyMethod, (jint) code);
        if (env->ExceptionCheck()) env->ExceptionClear();
        g_vm->DetachCurrentThread();
    }
    usleep(1800 * 1000);   // 토스트가 화면에 그려질 시간을 준 뒤 종료
    _exit(0);              // libc _exit — Frida 후킹 지점(raw syscall 아님)
}

// 킬 스레드: 로그인 화면이 뜰 시간을 준 뒤 안내 토스트 → 종료. (pthread_create 도 Frida 후킹 지점)
HIDDEN void *kill_thread(void *arg) {
    int code = (int) (long) arg;
    usleep(300 * 1000);
    show_message_and_die(code);
    return nullptr;
}

#else
// [INLINE] dlopen 시점 constructor 에서 탐지+종료를 동기·인라인으로 수행.
// 스레드/JNI 없음 → 후킹할 pthread_create/_exit 가 없다. 우회하려면 Ghidra 로 판정 함수를 찾아야 한다.

HIDDEN void enforce() {
    if (compromise_reason() >= 0) {
        sc_die();
    }
}

__attribute__((constructor))
HIDDEN static void guard_ctor() {
    enforce();
}

#endif  // GUARD_DEFER_MSG

}  // namespace

#ifdef GUARD_DEFER_MSG
// 라이브러리 로드 후 ART 가 호출. 여기서 토스트용 참조 확보 + 루팅/Frida 탐지 + 킬 개시.
// (생성자 .init_array 가 아니라 이 지점에서 탐지·킬이 시작된다 — Frida 로 도달/후킹 가능.)
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;
    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) == JNI_OK && env != nullptr) {
        jclass local = env->FindClass("com/hackmin/app/security/SecurityGuard");
        if (local != nullptr) {
            g_guardClass = (jclass) env->NewGlobalRef(local);
            g_notifyMethod = env->GetStaticMethodID(g_guardClass, "notifyTamper", "(I)V");
            env->DeleteLocalRef(local);
        }
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    g_vm = vm;

    // 탐지(루팅/Frida) → 감지 시 pthread_create 로 킬 스레드 개시(토스트 후 libc _exit).
    int reason = compromise_reason();
    if (reason >= 0) {
        pthread_t t;
        if (pthread_create(&t, nullptr, kill_thread, (void *) (long) reason) == 0) {
            pthread_detach(t);
        }
    }
    return JNI_VERSION_1_6;
}
#endif
