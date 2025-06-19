apply(plugin = "jacoco")

tasks.register<JacocoReport>("jacocoLogicReport") {
    group = "Reporting"
    description = "Generates a Jacoco test coverage report for UseCases and ViewModels only."

    val targetModules = subprojects.filter {
        it.plugins.hasPlugin("convention.jvm.jacoco") ||
                it.plugins.hasPlugin("convention.android.jacoco")
    }

    dependsOn(targetModules.map { module ->
        if (module.plugins.hasPlugin("com.android.base")) {
            module.tasks.named("testDebugUnitTest")
        } else {
            module.tasks.named("test")
        }
    })

    reports {
        xml.required.set(true)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacocoRootReport/html"))
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacocoRootReport/jacocoRootReport.xml"))
    }

    sourceDirectories.setFrom(files(targetModules.flatMap {
        listOf(
            "${it.projectDir}/src/main/java",
            "${it.projectDir}/src/main/kotlin"
        )
    }))

    // [수정] doFirst 블록을 제거하고, 클래스 경로를 설정할 때 제외 패턴을 바로 적용합니다.
    val excludedPatterns = listOf(
        "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
        "**/databinding/*Binding.*", "**/*_ViewBinding*.*",
        "**/di/*", "**/*_MembersInjector*.*", "**/*_Factory*.*", "**/*_Provide*Factory*.*", "**/*_HiltModules*.*",
        "**/*Directions*", "**/*Args*", "**/*Navigator*",
        "android/**/*.*"
    )
    classDirectories.setFrom(files(targetModules.map { module ->
        val buildDir = module.layout.buildDirectory.get().asFile
        val classPath = if (module.plugins.hasPlugin("com.android.base")) {
            "$buildDir/tmp/kotlin-classes/debug"
        } else {
            "$buildDir/classes/kotlin/main"
        }
        fileTree(classPath) {
            include(
                "**/*ViewModel.class",
                "**/*UseCase.class"
            )
            exclude(excludedPatterns)
        }
    }))

    executionData.setFrom(files(targetModules.map { module ->
        val buildDir = module.layout.buildDirectory.get().asFile
        val execFileName = if (module.plugins.hasPlugin("com.android.base")) {
            "testDebugUnitTest.exec"
        } else {
            "test.exec"
        }
        file("$buildDir/jacoco/$execFileName")
    }))

}