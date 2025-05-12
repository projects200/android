plugins {
    id("convention.android.library")
    id("convention.android.hilt")
}

android {
    namespace = "com.project200.undabang.data"

    defaultConfig {
        manifestPlaceholders["appAuthRedirectScheme"] = "com.project200.undabang"
    }

    buildTypes {
/*        getByName("debug") {
            buildConfigField("String", "BASE_URL", "\"https://api.undabang.store/dev/\"") // Debug용 URL
        }
        getByName("release") {
            buildConfigField("String", "BASE_URL", "\"https://api.undabang.store/\"") // Release용 URL
        }*/
    }
}

dependencies {
    implementation(projects.domain)
    implementation(projects.common)
    implementation(projects.core.oauth)

    // Networking
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.moshi.kotlin)

    // Firebase
    implementation(libs.firebase.config.ktx)
    ksp(libs.moshi.codegen)

    // Local DB (Room)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.androidx.arch.core.testing)

    implementation(libs.appauth)
}