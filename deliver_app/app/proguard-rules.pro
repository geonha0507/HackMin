# ==========================================================================
#  HackMin 릴리즈 난독화 규칙 (R8)
#
#  방침: 식별자 난독화는 켜되(minifyEnabled true), 아래 계층은 "흐름 파악이
#  가능한 수준"으로 남겨 둔다. 즉 앱 대부분(ui/network 구현/util)의 클래스·
#  메서드명은 a,b,c 로 치환되지만, 데이터 모델과 API 서비스 인터페이스는
#  그대로 유지되어 Retrofit 경로 문자열(@POST("auth/login") 등)로 통신 흐름을
#  추적할 수 있다. (진단 보고서 MOB-003의 "난독화 적용, 흐름 노출" 상태와 일치)
#
#  주의: R8은 식별자 치환일 뿐 문자열 상수는 난독화하지 않는다. 문자열 암호화·
#  제어흐름 난독화·핵심 로직 네이티브(.so) 이전은 별도 상용 도구/NDK 영역이다.
# ==========================================================================

# Gson 리플렉션이 필드명으로 JSON을 매핑하므로 DTO 모델은 필드까지 보존한다.
# (모델을 난독화하면 서버 JSON 키와 어긋나 파싱이 깨진다.)
-keep class com.hackmin.app.data.model.** { *; }

# Retrofit 서비스 인터페이스는 메서드명·어노테이션(경로 문자열)을 그대로 유지한다.
# → 디컴파일 시 API 흐름이 드러난다(의도된 잔여 노출).
-keep interface com.hackmin.app.data.api.** { *; }

# 보안 검사 클래스는 이름·메서드를 보존한다(모의해킹 실습: 문서화된 이름으로
# 후킹 우회를 재현할 수 있어야 함 — MOB-004 참조).
-keep class com.hackmin.app.security.** { *; }

# 입력 검문소(JNI): 클래스·네이티브 메서드명을 보존해야 libhackminfilter 의
# Java_..._nativeContainsXss 심볼과 링크가 유지된다(이름 바뀌면 UnsatisfiedLinkError).
-keep class com.hackmin.app.util.XssInputGuard { *; }

# ── 어노테이션/제네릭 시그니처 보존(Retrofit·Gson 동작에 필요) ──
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# ── Retrofit ──
-dontwarn retrofit2.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
# Retrofit 서비스 메서드의 제네릭 리턴 타입 보존.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ── OkHttp / Okio ──
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Gson ──
-dontwarn sun.misc.**
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── Glide ──
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**

# BuildConfig(로깅 스위치 등에서 참조).
-keep class com.hackmin.app.BuildConfig { *; }
