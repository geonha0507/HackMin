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

        // 네이티브 루팅 탐지(libhackminsec) 빌드 설정.
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
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