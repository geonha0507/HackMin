plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.hackmin.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hackmin.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 앱↔서버 페이로드 HMAC 서명 시크릿을 BuildConfig 로 주입한다(소스 하드코딩 금지).
        //  값은 gitignore 된 gradle.properties 또는 ~/.gradle/gradle.properties 의
        //  payloadHmacSecret 에서 읽는다. 미주입(빈 값)이면 PayloadCrypto.hasSecret()=false 라
        //  X-Sig 없이 전송(dual-mode) — 빌드/실행은 되고 PAYLOAD_ENFORCE 서버만 못 통과한다.
        buildConfigField(
            "String", "PAYLOAD_HMAC_SECRET",
            "\"${project.findProperty("payloadHmacSecret") ?: ""}\"",
        )

        // 서버 페이로드 공개키(SPKI base64). 비우면 소스의 기본값(개발용 키)을 쓴다.
        //  프로덕션(hackmin.com)은 다른 키쌍을 쓰므로, 그 대상 테스트 빌드는
        //  gitignore 된 gradle.properties 또는 ~/.gradle/gradle.properties 에
        //    payloadServerPublicKey=<프로덕션 공개키>
        //  를 넣어 주입한다(미주입이면 dev 키 → hackmin.com 붙으면 "세션키 복호화 실패").
        buildConfigField(
            "String", "PAYLOAD_SERVER_PUBLIC_KEY",
            "\"${project.findProperty("payloadServerPublicKey") ?: ""}\"",
        )

        // 네이티브 루팅 탐지(libhackminsec) 빌드 설정.
        //  가드 종료 방식을 빌드 플래그로 전환한다(기본 inline):
        //    ./gradlew assembleRelease                     → inline  (탐지 즉시 조용히 종료, Ghidra 필수)
        //    ./gradlew assembleRelease -PguardMode=message  → message (로그인 후 "루팅 감지" 토스트→종료)
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DGUARD_MODE=${project.findProperty("guardMode") ?: "inline"}"
            }
        }
        // 실기기(arm64)·에뮬레이터(x86_64) 커버. 필요 시 ABI 추가 가능.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    buildTypes {
        release {
            // R8 식별자 난독화 활성화(문자열 상수는 그대로 노출 — proguard-rules.pro 참고).
            // 데이터 모델·API 인터페이스·보안 검사 클래스는 keep 규칙으로 흐름 추적이
            // 가능하도록 남겨 둔다.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // [훈련용] release 를 debug 키스토어로 서명해 바로 설치 가능하게 한다.
            //  자체 취약 훈련앱이라 서명 키 정체성은 무의미(Play 배포 없음). 실배포용이면
            //  별도 릴리즈 키스토어로 교체할 것. → 커밋/머지 전 검토 필요.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    // ApiClient가 BuildConfig.DEBUG로 네트워크 로깅을 껐다 켜므로 생성이 필요하다.
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation("com.google.code.gson:gson:2.10.1")
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Image loading (review photos from S3)
    // 이미지 로딩 (음식점/메뉴 사진)
    implementation("com.github.bumptech.glide:glide:4.16.0")
// Testing (unit tests, run on JVM, no emulator needed)
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

}