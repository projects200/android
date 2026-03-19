plugins {
    id("convention.android.library")
    id("convention.android.hilt")
    alias(libs.plugins.navigation.safeargs)
}

android {
    namespace = "com.project200.undabang.feature.feed"

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(projects.domain)
    implementation(projects.common)
    implementation(projects.presentation)

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

    // CircleImageView
    implementation(libs.circleimageview)

    // ViewPager2 Indicator
    implementation(libs.circleindicator)

    // Shimmer
    implementation(libs.shimmer)

    // SwipeRefreshLayout
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
