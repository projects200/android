plugins {
    id("convention.android.library")
    id("convention.android.hilt")
}

android {
    namespace = "com.project200.undabang.common"

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
}