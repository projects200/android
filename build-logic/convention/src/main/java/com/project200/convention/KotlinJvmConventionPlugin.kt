package com.project200.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class KotlinJvmConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // 헬퍼를 사용하여 플러그인 적용
            pluginManager.apply(libs.plugin("kotlin-jvm").get().pluginId)

            // 코틀린 JVM 설정 헬퍼 함수 호출
            configureKotlinJvm()

            // 헬퍼를 사용하여 공통 의존성 추가
            dependencies {
                "implementation"(libs.library("kotlin-stdlib"))
                "implementation"(libs.library("kotlinx-coroutines-core"))
                "implementation"(libs.library("javax-inject")) // JSR 330

                "testImplementation"(libs.library("junit"))
                "testImplementation"(libs.library("kotlinx-coroutines-test"))
                "testImplementation"(libs.library("mockk"))
            }
        }
    }
}