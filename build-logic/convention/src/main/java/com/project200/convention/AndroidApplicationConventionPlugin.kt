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
            pluginManager.apply(libs.plugin("google-services").get().pluginId)
            pluginManager.apply(libs.plugin("firebase-performance").get().pluginId)
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
                        isDebuggable = true
                    }
                }
                // 코틀린 관련 설정 헬퍼 함수 호출
                configureKotlinAndroid()

                lint {
                    htmlReport = true
                    xmlReport = true
                    abortOnError = true
                    warningsAsErrors = false
                }

                packaging {
                    resources {
                        // mockk 충돌문제
                        excludes.add("META-INF/LICENSE-notice.md")
                        excludes.add("META-INF/LICENSE.md")
                    }
                }
            }

            dependencies {
                "implementation"(libs.library("kotlin-stdlib"))
                "implementation"(libs.library("androidx-core-ktx"))
                "implementation"(libs.library("androidx-splashscreen"))
                "implementation"(libs.library("timber"))

                "implementation"(platform(libs.library("firebase-bom")))
                "implementation"(libs.library("firebase-analytics"))
                "implementation"(libs.library("firebase-performance"))
                "implementation"(libs.library("firebase-messaging"))

                "testImplementation"(libs.library("junit"))
                "testImplementation"(libs.library("mockk"))
                "testImplementation"(libs.library("kotlinx-coroutines-test")) // 코루틴 테스트
                "testImplementation"(libs.library("turbine")) // Flow 테스트
                "testImplementation"(libs.library("androidx-arch-core-testing")) // LiveData/ViewModel 테스트용
                "testImplementation"(libs.library("truth"))


                "androidTestImplementation"(libs.library("androidx-test-ext-junit"))
                "androidTestImplementation"(libs.library("androidx-test-espresso-core"))
                "androidTestImplementation"(libs.library("mockk"))
                "androidTestImplementation"(libs.library("kotlinx-coroutines-test")) // 코루틴 테스트
            }
        }
    }
}