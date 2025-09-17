plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.hilt.gradle.plugin)
    compileOnly(libs.ktlint.gradle.plugin)
    implementation(libs.firebase.performance.plugin)
}

gradlePlugin {
    plugins {
        register("conventionAndroidApplication") { // Gradle 내부 등록 이름 (자유롭게 지정)
            id = "convention.android.application" // 모듈에서 사용할 최종 ID
            implementationClass = "com.project200.convention.AndroidApplicationConventionPlugin" // 실제 클래스 경로
        }
        register("conventionAndroidLibrary") {
            id = "convention.android.library"
            implementationClass = "com.project200.convention.AndroidLibraryConventionPlugin"
        }
        register("conventionAndroidHilt") {
            id = "convention.android.hilt"
            implementationClass = "com.project200.convention.AndroidHiltConventionPlugin"
        }
        register("conventionKotlinJvm") {
            id = "convention.kotlin.jvm"
            implementationClass = "com.project200.convention.KotlinJvmConventionPlugin"
        }
        register("conventionJvmJacoco") {
            id = "convention.jvm.jacoco"
            implementationClass = "com.project200.convention.JvmJacocoConventionPlugin"
        }
        register("conventionAndroidJacoco") {
            id = "convention.android.jacoco"
            implementationClass = "com.project200.convention.AndroidJacocoConventionPlugin"
        }
        register("conventionKtlint") {
            id = "convention.ktlint"
            implementationClass = "com.project200.convention.KtlintConventionPlugin"
        }
    }
}