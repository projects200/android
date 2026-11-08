plugins {
    id("convention.android.library")
    id("convention.android.hilt")
    id("convention.android.compose")
    alias(libs.plugins.navigation.safeargs)
    id("convention.android.jacoco")
}

android {
    namespace = "com.project200.undabang.feature.exercise"

    defaultConfig {
        manifestPlaceholders["appAuthRedirectScheme"] = "com.project200.undabang"
    }

    testOptions {
        // 기본 targetSdk가 minSdk인 테스트 APK의 구버전 경고가 포커스를 가로채므로 프로젝트 타깃을 사용합니다
        targetSdk = libs.versions.targetSdk.get().toInt()
    }
}

dependencies {
    implementation(projects.domain)
    implementation(projects.common)
    implementation(projects.presentation)
    implementation(projects.core.oauth)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.google.android.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.navigation.testing)

    // Glide
    implementation(libs.glide)
    ksp(libs.glide.compiler.ksp)

    // Coil (Compose)
    implementation(libs.coil.compose)

    // CirlceIndicator
    implementation(libs.circleindicator)

    // 캘린더
    implementation(libs.kizitonwose.calendar.view)

    // lottie animation
    implementation(libs.lottie)

    // Shimmer
    implementation(libs.shimmer)

    // Google Maps
    implementation(libs.play.services.location)

    // Kakao Map
    implementation(libs.kakao.map)
}
