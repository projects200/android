plugins {
    id("convention.android.library")
    id("convention.android.hilt")
}

android {
    namespace = "com.project200.undabang.data"
}

dependencies {
    implementation(projects.domain)
    implementation(projects.common)

    // Networking
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    // Local DB (Room)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.androidx.arch.core.testing)
}