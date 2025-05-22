plugins {
    id("convention.android.application")
    alias(libs.plugins.navigation.safeargs)
}

android {
    namespace = "com.project200.undabang"

    defaultConfig {
        manifestPlaceholders["appAuthRedirectScheme"] = "com.project200.undabang"
    }

    signingConfigs { /* ... 출시 서명 설정 ... */ }

    buildTypes {
        release {
        }
        debug {
        }
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(projects.presentation)
    implementation(projects.data)
    implementation(projects.common)
    implementation(projects.domain)
    implementation(projects.core.oauth)
    implementation(projects.feature.auth)
    implementation(projects.feature.profile)
    implementation(projects.feature.exercise)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.google.android.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity)

    implementation(libs.androidx.appcompat)
    implementation(libs.google.android.material)
    implementation(libs.androidx.constraintlayout)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    implementation(libs.appauth)
}