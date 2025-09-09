import java.util.Properties

plugins {
    id("convention.android.library")
    id("convention.android.hilt")
}

android {
    namespace = "com.project200.undabang.core.oauth"

    defaultConfig {
        manifestPlaceholders["appAuthRedirectScheme"] = "com.project200.undabang"
    }

    buildTypes {
        val properties =
            Properties().apply { // java.util.Properties 대신 Properties() 사용 가능
                val localPropertiesFile = rootProject.file("local.properties")
                if (localPropertiesFile.exists()) {
                    localPropertiesFile.inputStream().use { load(it) }
                }
            }

        debug {
            // 개발용 Cognito 사용자 풀 정보
            buildConfigField("String", "COGNITO_USER_POOL_ID", "\"${properties.getProperty("COGNITO_USER_POOL_ID_DEV") ?: ""}\"")
            buildConfigField("String", "COGNITO_APP_CLIENT_ID", "\"${properties.getProperty("COGNITO_APP_CLIENT_ID_DEV") ?: ""}\"")
            buildConfigField("String", "COGNITO_REGION", "\"${properties.getProperty("COGNITO_REGION_DEV") ?: ""}\"")
        }
        release {
            // 릴리즈용 Cognito 사용자 풀 정보
            buildConfigField("String", "COGNITO_USER_POOL_ID", "\"${properties.getProperty("COGNITO_USER_POOL_ID") ?: ""}\"")
            buildConfigField("String", "COGNITO_APP_CLIENT_ID", "\"${properties.getProperty("COGNITO_APP_CLIENT_ID") ?: ""}\"")
            buildConfigField("String", "COGNITO_REGION", "\"${properties.getProperty("COGNITO_REGION") ?: ""}\"")
        }
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

    // EncryptedSharedPreferences
    implementation(libs.androidx.security.crypto)
}
