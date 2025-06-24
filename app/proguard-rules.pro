# Moshi와 관련된 모든 클래스 및 어댑터 클래스를 유지합니다.
-keep class com.squareup.moshi.** { *; }
-keep class com.project200.data.dto.** { *; } # data 모듈의 모든 DTO 클래스를 유지
-keep class com.project200.domain.model.** { *; } # domain 모듈의 모델 클래스도 함께 유지 (안정성 강화)

# Kotlin reflection을 사용하는 Moshi 어댑터 생성을 위해 메타데이터를 유지합니다.
-keep class kotlin.Metadata { *; }

# @JsonClass(generateAdapter = true)로 생성된 어댑터 클래스들을 유지합니다.
# Moshi의 KotlinJsonAdapterFactory가 런타임에 생성하는 어댑터 클래스 패턴입니다.
# 일반적으로 DTO 클래스명 뒤에 "JsonAdapter"가 붙습니다.
-keep class **.*JsonAdapter { *; }

# Hilt 및 기타 DI 관련 클래스 (이미 convention plugin에 있을 수 있지만, 명시적으로 추가하여 충돌 방지)
-keep class dagger.hilt.** { *; }
-keep class com.project200.undabang.ApplicationClass
-keep class * extends androidx.fragment.app.Fragment { <init>(...); }
-keep class * extends androidx.activity.ComponentActivity { <init>(...); }
-keepclasseswithmembers class * {
    @dagger.Provides <methods>;
}
-keepclasseswithmembers class * {
    @dagger.hilt.android.qualifiers.ApplicationContext <init>(...);
}