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

    // Testing (unit tests, run on JVM, no emulator needed)
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
