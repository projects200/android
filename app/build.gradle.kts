import java.util.Properties

plugins {
    id("convention.android.application")
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.firebase.crashlytics)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
val kakaoNativeAppKey: String =
    localProperties.getProperty("KAKAO_NATIVE_APP_KEY")
        ?: System.getenv("KAKAO_NATIVE_APP_KEY")

val kakaoRestApiKey: String =
    localProperties.getProperty("KAKAO_REST_API_KEY")
        ?: System.getenv("KAKAO_REST_API_KEY")

val maestroE2eStoreFile = project.findProperty("maestro.e2e.signing.store.file")?.toString()
val maestroE2eStorePassword = project.findProperty("maestro.e2e.signing.store.password")?.toString()
val maestroE2eKeyAlias = project.findProperty("maestro.e2e.signing.key.alias")?.toString()
val maestroE2eKeyPassword = project.findProperty("maestro.e2e.signing.key.password")?.toString()
val hasMaestroE2eSigning =
    listOf(maestroE2eStoreFile, maestroE2eStorePassword, maestroE2eKeyAlias, maestroE2eKeyPassword)
        .all { it != null }

android {
    namespace = "com.project200.undabang"

    defaultConfig {
        manifestPlaceholders["appAuthRedirectScheme"] = "com.project200.undabang"
        manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = kakaoNativeAppKey
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"${kakaoNativeAppKey}\"")
        buildConfigField("String", "KAKAO_REST_API_KEY", "\"${kakaoRestApiKey}\"")
        resValue("bool", "timer_sync_enabled", "true")

        ndkVersion = "28.0.10027231"

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    signingConfigs {
        create("release") {
            // 워크플로우의 -P 옵션으로 전달된 프로젝트 속성을 읽어옵니다.
            val storeFile = project.findProperty("android.injected.signing.store.file")
            val storePassword = project.findProperty("android.injected.signing.store.password")
            val keyAlias = project.findProperty("android.injected.signing.key.alias")
            val keyPassword = project.findProperty("android.injected.signing.key.password")

            // 속성들이 CI/CD 환경에서 전달되었을 때만 서명 설정을 적용합니다.
            // 로컬에서 개발할 때는 이 값이 없어도 에러가 발생하지 않습니다.
            if (storeFile != null && storePassword != null && keyAlias != null && keyPassword != null) {
                this.storeFile = rootProject.file(storeFile.toString())
                this.storePassword = storePassword.toString()
                this.keyAlias = keyAlias.toString()
                this.keyPassword = keyPassword.toString()
            }
        }
        if (hasMaestroE2eSigning) {
            create("maestroE2e") {
                storeFile = rootProject.file(maestroE2eStoreFile!!)
                storePassword = maestroE2eStorePassword
                keyAlias = maestroE2eKeyAlias
                keyPassword = maestroE2eKeyPassword
            }
        }
    }

    buildTypes {
        release {
            resValue("string", "app_name", "운다방")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            resValue("string", "app_name", "운다방(Debug)")
        }
        create("e2e") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            signingConfig =
                if (hasMaestroE2eSigning) {
                    signingConfigs.getByName("maestroE2e")
                } else {
                    signingConfigs.getByName("debug")
                }
            resValue("bool", "timer_sync_enabled", "false")
            resValue("string", "app_name", "운다방(E2E)")
        }
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(projects.presentation)
    implementation(projects.data)
    implementation(projects.common)
    implementation(projects.domain)
    implementation(projects.core.oauth)
    implementation(projects.feature.auth)
    implementation(projects.feature.profile)
    implementation(projects.feature.exercise)
    implementation(projects.feature.timer)
    implementation(projects.feature.matching)
    implementation(projects.feature.feed)
    implementation(projects.feature.chatting)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.google.android.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity)

    implementation(libs.androidx.appcompat)
    implementation(libs.google.android.material)
    implementation(libs.androidx.constraintlayout)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // WorkManager (FCM 토큰 전송 예약)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    "ksp"(libs.androidx.hilt.compiler)

    implementation(libs.appauth)

    // Kakao Map
    implementation(libs.kakao.map)

    implementation(libs.firebase.crashlytics.ktx)

    // E2E 진단에서 기존 네트워크 예외 타입만 참조하며 일반 빌드 의존성은 바꾸지 않습니다.
    "e2eImplementation"(libs.retrofit.core)
    "e2eImplementation"(libs.moshi.kotlin)
    "e2eImplementation"(libs.androidx.room.runtime)
    "e2eImplementation"(libs.androidx.room.ktx)

    "testE2eImplementation"(libs.robolectric)
    testImplementation(libs.robolectric)
}
