plugins {
    id("convention.android.application")
    alias(libs.plugins.navigation.safeargs)
}

android {
    namespace = "com.project200.myapp"

    signingConfigs { /* ... 출시 서명 설정 ... */ }

    buildTypes {
        release {
        }
        debug {
        }
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
}

dependencies {
    implementation(projects.presentation)
    implementation(projects.data)
    implementation(projects.common)
    implementation(projects.domain)

    implementation(libs.androidx.appcompat)
    implementation(libs.google.android.material)
    implementation(libs.androidx.constraintlayout)
}