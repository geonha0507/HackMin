# 네이티브 보안 가드(libhackminsec) 빌드.
# 루팅·Frida 탐지/차단을 C++로 수행하며, 판정 함수는 내보내지 않고(hidden) strip 되어
# .so에서 이름이 sub_XXXX 로만 보이도록 한다(IDA/Ghidra로 offset을 찾아야 후킹 가능).
cmake_minimum_required(VERSION 3.22.1)

project("hackminsec")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(hackminsec SHARED native_guard.cpp)

# 심볼 숨김: JNIEXPORT(=default visibility)로 명시한 심볼(JNI_OnLoad)만 export 되고,
# 나머지 탐지/판정 함수는 동적 심볼 테이블에서 제거된다.
target_compile_options(hackminsec PRIVATE
        -fvisibility=hidden
        -fvisibility-inlines-hidden
        -ffunction-sections
        -fdata-sections
        -fno-exceptions
        -fno-rtti)

# 링크 단계: 미사용 섹션 제거, 정적 라이브러리 심볼 숨김.
# release(RelWithDebInfo)에서는 --strip-all 로 심볼 테이블까지 제거해 함수 이름을 지운다.
target_link_options(hackminsec PRIVATE
        -Wl,--gc-sections
        -Wl,--exclude-libs,ALL
        "$<$<NOT:$<CONFIG:Debug>>:-Wl,--strip-all>")

