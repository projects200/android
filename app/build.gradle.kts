plugins {
    id("convention.android.application")
    alias(libs.plugins.navigation.safeargs)
}

android {
    namespace = "com.project200.undabang"

    signingConfigs { /* ... 출시 서명 설정 ... */ }

    defaultConfig {
        manifestPlaceholders["appAuthRedirectScheme"] = "com.project200.undabang"
    }

    buildTypes {
        release {
        }
        debug {
        }
    }
}

dependencies {
    implementation(projects.presentation)
    implementation(projects.data)
    implementation(projects.common)
    implementation(projects.domain)
    implementation(projects.feature.auth)

    implementation(libs.androidx.appcompat)
    implementation(libs.google.android.material)
    implementation(libs.androidx.constraintlayout)
}