package com.project200.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.tasks.JacocoReport

class JvmJacocoConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.plugin("jacoco").get().pluginId)

            tasks.withType<Test>().configureEach {
                finalizedBy(tasks.withType<JacocoReport>())
            }

            // 생성될 리포트의 기본 설정을 정의
            tasks.withType<JacocoReport>().configureEach {
                reports {
                    xml.required.set(true)
                    html.required.set(true)
                }
            }
        }
    }
}