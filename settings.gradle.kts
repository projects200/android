enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}


dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
    }
}
rootProject.name = "undabang"
include(":app")
include(":domain")
include(":data")
include(":presentation")
include(":common")
include(":feature:auth")
include(":core:oauth")
include(":feature:profile")
include(":feature:exercise")
include(":feature:timer")
include(":feature:matching")
