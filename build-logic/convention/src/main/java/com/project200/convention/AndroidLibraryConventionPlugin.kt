package com.project200.convention

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // 헬퍼를 사용하여 플러그인 적용
            pluginManager.apply(libs.plugin("android-library").get().pluginId)
            pluginManager.apply(libs.plugin("kotlin-android").get().pluginId)

            // Android 공통 설정
            extensions.configure<LibraryExtension> {
                compileSdk = libs.version("compileSdk").toInt() // version 헬퍼 사용
                defaultConfig {
                    minSdk = libs.version("minSdk").toInt() // version 헬퍼 사용
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    consumerProguardFiles("consumer-rules.pro")
                }
                buildTypes {
                    release {
                        isMinifyEnabled = false
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro"
                        )
                    }
                }

                buildFeatures {
                    viewBinding = true
                    buildConfig = true
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

            // 헬퍼를 사용하여 공통 의존성 추가
            dependencies {
                "implementation"(libs.library("kotlin-stdlib"))
                "implementation"(libs.library("androidx-core-ktx"))
                "implementation"(libs.library("kotlinx-coroutines-android"))
                "implementation"(libs.library("timber"))

                "testImplementation"(libs.library("junit"))
                "testImplementation"(libs.library("mockk"))
                "testImplementation"(libs.library("kotlinx-coroutines-test")) // 코루틴 테스트
                "testImplementation"(libs.library("turbine")) // Flow 테스트
                "testImplementation"(libs.library("androidx-arch-core-testing")) // LiveData/ViewModel 테스트용


                "androidTestImplementation"(libs.library("androidx-test-ext-junit"))
                "androidTestImplementation"(libs.library("androidx-test-espresso-core"))
                "androidTestImplementation"(libs.library("mockk")) // MockK 추가
                "androidTestImplementation"(libs.library("kotlinx-coroutines-test")) // 코루틴 테스트
            }
        }
    }
}