plugins {
    id("convention.android.library")
}

android {
    namespace = "com.project200.myapp.common"

    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
}