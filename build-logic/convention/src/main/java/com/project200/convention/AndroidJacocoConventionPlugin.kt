package com.project200.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

class AndroidJacocoConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            plugins.withId("com.android.base") {
                pluginManager.apply(libs.findPlugin("jacoco").get().get().pluginId)
            }

            tasks.withType<Test>().configureEach {
                if (name == "testDebugUnitTest") {
                    configure<JacocoTaskExtension> {
                        isEnabled = true
                    }
                }
            }
        }
    }
}