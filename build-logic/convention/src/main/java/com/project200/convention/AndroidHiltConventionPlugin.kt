package com.project200.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // 플러그인 적용 부분은 동일
            pluginManager.apply(libs.plugin("hilt").get().pluginId)
            pluginManager.apply(libs.plugin("ksp").get().pluginId)

            // 의존성 추가 시 문자열 invoke 연산자 사용
            dependencies {
                "implementation"(libs.library("hilt-android"))
                "ksp"(libs.library("hilt-compiler"))

                "testImplementation"(libs.library("hilt-android-testing"))
                "kspTest"(libs.library("hilt-compiler"))

                "androidTestImplementation"(libs.library("hilt-android-testing"))
                "kspAndroidTest"(libs.library("hilt-compiler"))
            }
        }
    }
}