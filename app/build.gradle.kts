import java.util.Properties

plugins {
    id("convention.android.application")
    alias(libs.plugins.navigation.safeargs)
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

android {
    namespace = "com.project200.undabang"

    defaultConfig {
        manifestPlaceholders["appAuthRedirectScheme"] = "com.project200.undabang"
        manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = kakaoNativeAppKey
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"${kakaoNativeAppKey}\"")
        buildConfigField("String", "KAKAO_REST_API_KEY", "\"${kakaoRestApiKey}\"")

        ndkVersion = "28.0.10027231"
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
    }

    buildTypes {
        release {
            resValue("string", "app_name", "운다방")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            resValue("string", "app_name", "운다방(Debug)")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            // 카카오맵 SDK가 지원하는 ARM 아키텍처만 포함시킵니다.
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
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

    implementation(libs.appauth)

    // Kakao Map
    implementation(libs.kakao.map)
}
