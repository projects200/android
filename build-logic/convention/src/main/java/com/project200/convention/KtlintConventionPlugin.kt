package com.project200.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

internal class KtlintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.plugin("ktlint").get().pluginId)

            dependencies {
                "ktlint"(libs.findLibrary("ktlint-cli").get())
            }
        }
    }
}