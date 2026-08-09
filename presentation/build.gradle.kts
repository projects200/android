plugins {
    id("convention.android.library")
    id("convention.android.hilt")
    id("convention.android.compose")
    alias(libs.plugins.navigation.safeargs)
}

android {
    namespace = "com.project200.undabang.presentation"

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(projects.domain)
    implementation(projects.common)

    // Image Loading
    implementation(libs.coil.compose)

    // Map (KakaoMapView 공통 컴포저블 호스팅용)
    implementation(libs.kakao.map)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.splashscreen)
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
}
