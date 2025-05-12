plugins {
    id("convention.android.library")
    id("convention.android.hilt")
}

android {
    namespace = "com.project200.undabang.core.oauth"

    defaultConfig {
        manifestPlaceholders["appAuthRedirectScheme"] = "com.project200.undabang"
    }

    defaultConfig {
        buildConfigField("String", "COGNITO_USER_POOL_ID", "\"${project.findProperty("COGNITO_USER_POOL_ID") ?: ""}\"")
        buildConfigField("String", "COGNITO_APP_CLIENT_ID", "\"${project.findProperty("COGNITO_APP_CLIENT_ID") ?: ""}\"")
        buildConfigField("String", "COGNITO_REGION", "\"${project.findProperty("COGNITO_REGION") ?: ""}\"")

    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.android.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)

    // Cognito
    implementation(libs.appauth)
}