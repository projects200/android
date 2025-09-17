package com.project200.convention

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.plugin.use.PluginDependency

// Project 확장 프로퍼티로 'libs' VersionCatalog 에 쉽게 접근
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

// VersionCatalog 확장 함수로 라이브러리 접근 단순화
// 예: libs.library("androidx-core-ktx") -> Provider<MinimalExternalModuleDependency> 반환
internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> {
    return findLibrary(alias).get() // .get()은 해당 alias가 존재함을 가정
}

// VersionCatalog 확장 함수로 플러그인 접근 단순화
// 예: libs.plugin("android-library") -> Provider<PluginDependency> 반환
internal fun VersionCatalog.plugin(alias: String): Provider<PluginDependency> {
    return findPlugin(alias).get() // .get()은 해당 alias가 존재함을 가정
}

// VersionCatalog 확장 함수로 버전 접근 단순화 (필요시)
// 예: libs.version("compileSdk") -> "34" 반환
internal fun VersionCatalog.version(alias: String): String {
    return findVersion(alias).get().toString() // .get()은 해당 alias가 존재함을 가정
}

// KotlinCompile 작업에 대한 jvmTarget 설정을 위한 확장 함수 (예시)
internal fun Project.configureKotlinJvm() {
    tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
        kotlinOptions {
            // 필요시 libs.version("jvmTargetVersion") 등으로 버전 관리
            jvmTarget = org.gradle.api.JavaVersion.VERSION_17.toString()
        }
    }
    // Kotlin Toolchain 설정 추가 (JavaVersion import 필요)
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension> {
        jvmToolchain(17) // 또는 libs.version("java-toolchain").get().toInt()
    }
}
// Android Kotlin 설정 함수 (예시)
internal fun com.android.build.api.dsl.CommonExtension<*, *, *, *, *, *>.configureKotlinAndroid() {
    compileOptions {
        sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
        targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
    }
    (this as ExtensionAware).extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions>("kotlinOptions") {
        jvmTarget = org.gradle.api.JavaVersion.VERSION_17.toString()
        // freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
    }
}