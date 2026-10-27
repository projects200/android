import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoReportBase
import java.math.BigDecimal

apply(plugin = "jacoco")

val targetModules = subprojects.filter {
    it.plugins.hasPlugin("convention.jvm.jacoco") ||
        it.plugins.hasPlugin("convention.android.jacoco")
}

val excludedPatterns = listOf(
    "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
    "**/databinding/*Binding.*", "**/*_ViewBinding*.*",
    "**/di/*", "**/*_MembersInjector*.*", "**/*_Factory*.*", "**/*_Provide*Factory*.*", "**/*_HiltModules*.*",
    "**/*Directions*", "**/*Args*", "**/*Navigator*",
    "android/**/*.*",
)

fun JacocoReportBase.configureLogicCoverageInputs() {
    sourceDirectories.setFrom(
        files(
            targetModules.flatMap {
                listOf(
                    "${it.projectDir}/src/main/java",
                    "${it.projectDir}/src/main/kotlin",
                )
            },
        ),
    )

    classDirectories.setFrom(
        files(
            targetModules.map { module ->
                val buildDir = module.layout.buildDirectory.get().asFile
                val classPath =
                    if (module.plugins.hasPlugin("com.android.base")) {
                        "$buildDir/tmp/kotlin-classes/debug"
                    } else {
                        "$buildDir/classes/kotlin/main"
                    }
                fileTree(classPath) {
                    include(
                        "**/*ViewModel.class",
                        "**/*UseCase.class",
                    )
                    exclude(excludedPatterns)
                }
            },
        ),
    )

    executionData.setFrom(
        files(
            targetModules.map { module ->
                val buildDir = module.layout.buildDirectory.get().asFile
                val execFileName =
                    if (module.plugins.hasPlugin("com.android.base")) {
                        "testDebugUnitTest.exec"
                    } else {
                        "test.exec"
                    }
                file("$buildDir/jacoco/$execFileName")
            },
        ),
    )
}

fun JacocoReportBase.configureLogicCoverageTests() {
    dependsOn(
        targetModules.map { module ->
            if (module.plugins.hasPlugin("com.android.base")) {
                module.tasks.named("testDebugUnitTest")
            } else {
                module.tasks.named("test")
            }
        },
    )
}

tasks.register<JacocoReport>("jacocoLogicReport") {
    group = "Reporting"
    description = "Generates a Jacoco test coverage report for UseCases and ViewModels only."

    configureLogicCoverageTests()
    configureLogicCoverageInputs()

    reports {
        xml.required.set(true)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacocoRootReport/html"))
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacocoRootReport/jacocoRootReport.xml"))
    }
}

tasks.register<JacocoCoverageVerification>("jacocoLogicCoverageVerification") {
    group = "Verification"
    description = "Fails when UseCase and ViewModel line coverage is below 80%."

    configureLogicCoverageTests()
    configureLogicCoverageInputs()

    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = BigDecimal("0.80")
            }
        }
    }
}
