package com.project200.convention

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.configure<LibraryExtension> {
                buildFeatures {
                    compose = true
                }
                composeOptions {
                    kotlinCompilerExtensionVersion = libs.version("composeCompiler")
                }
            }

            dependencies {
                val bom = libs.library("compose-bom")
                "implementation"(platform(bom))
                "implementation"(libs.library("compose-ui"))
                "implementation"(libs.library("compose-ui-graphics"))
                "implementation"(libs.library("compose-ui-tooling-preview"))
                "implementation"(libs.library("compose-foundation"))
                "implementation"(libs.library("compose-material3"))
                "implementation"(libs.library("activity-compose"))
                "implementation"(libs.library("lifecycle-viewmodel-compose"))
                "implementation"(libs.library("lifecycle-runtime-compose"))

                "debugImplementation"(libs.library("compose-ui-tooling"))
                "debugImplementation"(libs.library("compose-ui-test-manifest"))

                "androidTestImplementation"(platform(bom))
                "androidTestImplementation"(libs.library("compose-ui-test-junit4"))
            }
        }
    }
}
