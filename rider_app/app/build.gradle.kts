plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.hackmin.connect"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hackmin.connect"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // PayloadCrypto가 요청 서명(X-Sig)에 쓰는 HMAC 시크릿. 서버
        // PAYLOAD_APP_HMAC_SECRET 와 같은 값을 gitignore 된 gradle.properties 에
        //   payloadHmacSecret=...
        // 로 넣으면 빌드 시 주입된다. 비어 있으면 X-Sig 없이 전송(듀얼 모드 전용).
        buildConfigField(
            "String", "PAYLOAD_HMAC_SECRET",
            "\"${project.findProperty("payloadHmacSecret") ?: ""}\""
        )

        // dex 루팅 가드 on/off. 기본 켜짐(실습용). 루팅 에뮬(LDPlayer)에서
        // 개발/테스트하려면 gitignore 된 gradle.properties 에 rootGuard=false 를
        // 넣거나 -ProotGuard=false 로 끈다. SecurityGuard 가 이 값을 읽는다.
        buildConfigField(
            "boolean", "ROOT_GUARD",
            "${project.findProperty("rootGuard") ?: "true"}"
        )
    }

    buildTypes {
        release {
            // 라이더 앱은 훈련 표적이 아니므로 난독화 없이 배포한다(빌드 단순화).
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    // ApiClient가 BuildConfig.DEBUG로 네트워크 로깅을 껐다 켜고,
    // PayloadCrypto가 BuildConfig.PAYLOAD_HMAC_SECRET 를 읽으므로 생성이 필요하다.
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

    // Networking (deliver_app과 동일 버전)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // 이미지 로딩 (매장 사진) — deliver_app과 동일 버전
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // 실시간 위치: FusedLocationProvider(고정밀·저전력). GMS 없으면 LocationManager로 폴백.
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // 실제 지도(OpenStreetMap) — API 키 없이 지도 타일 + 내 위치 마커 표시.
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Testing (unit tests, run on JVM, no emulator needed)
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
