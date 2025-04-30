package com.project200.convention

import com.android.build.api.dsl.ApplicationExtension // ApplicationExtension import
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.plugin("android-application").get().pluginId)
            pluginManager.apply(libs.plugin("kotlin-android").get().pluginId)
            pluginManager.apply("convention.android.hilt")

            // Android 애플리케이션 공통 설정
            extensions.configure<ApplicationExtension> {
                compileSdk = libs.version("compileSdk").toInt()
                defaultConfig {
                    minSdk = libs.version("minSdk").toInt()
                    targetSdk = libs.version("targetSdk").toInt()
                    versionCode = libs.version("versionCode").toInt()
                    versionName = libs.version("versionName")
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                buildTypes {
                    release {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro"
                        )
                    }
                    debug {
                        applicationIdSuffix = ".debug"
                        isDebuggable = true
                    }
                }
                // 코틀린 관련 설정 헬퍼 함수 호출
                configureKotlinAndroid()
            }

            dependencies {
                "implementation"(libs.library("kotlin-stdlib"))
                "implementation"(libs.library("androidx-core-ktx"))
                "implementation"(libs.library("timber"))

                "testImplementation"(libs.library("junit"))
                "androidTestImplementation"(libs.library("androidx-test-ext-junit"))
                "androidTestImplementation"(libs.library("androidx-test-espresso-core"))
            }
        }
    }
}