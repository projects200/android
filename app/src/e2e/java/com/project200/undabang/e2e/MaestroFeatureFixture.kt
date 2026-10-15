package com.project200.undabang.e2e

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import org.json.JSONObject

internal data class MaestroFeatureRequest(
    val feature: String,
    val flow: String,
    val runId: String,
    val memberId: String,
    val parameters: JSONObject,
)

internal interface MaestroFeatureFixture {
    suspend fun prepare(request: MaestroFeatureRequest): Map<String, String>

    /**
     * 같은 feature/flow/runId/memberId의 재시도에도 복구를 완료해야 합니다.
     * 후속 운동 정리나 세션 제거가 실패할 수 있으므로 snapshot과 ownership 증거를 삭제하지 않습니다.
     * 공통 정리가 성공한 뒤 호스트의 최종 pm clear가 성공할 때만 해당 앱 private 증거를 제거합니다.
     * 소유권 불일치와 실제 복구 실패는 계속 오류로 반환합니다.
     */
    suspend fun cleanup(request: MaestroFeatureRequest)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MaestroFeatureFixtureModule {
    // 기능 PR이 하나도 설치되지 않은 공통 기준 트리도 빈 Map으로 주입합니다.
    @Multibinds
    abstract fun fixtures(): Map<String, MaestroFeatureFixture>
}

internal object MaestroFeaturePolicy {
    private val featurePattern = Regex("[a-z][a-z0-9-]*")
    private val runPattern = Regex("[a-f0-9]{16,32}")
    private val variablePattern = Regex("[A-Z][A-Z0-9_]*")
    private val privateKeyPattern = Regex("AUTH|TOKEN|FCM|SECRET|PASSWORD|SESSION")
    private val jwtPattern = Regex("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.")

    fun validateIdentity(
        feature: String,
        flow: String,
        runId: String,
    ) {
        if (!featurePattern.matches(feature) || !featurePattern.matches(flow) || !runPattern.matches(runId)) {
            throw MaestroSetupFailure("invalid_feature_request")
        }
    }

    fun validateVariables(variables: Map<String, String>) {
        for ((key, value) in variables) {
            if (
                !variablePattern.matches(key) ||
                privateKeyPattern.containsMatchIn(key) ||
                value.length > 4096 ||
                value.any { it.code < 32 } ||
                jwtPattern.containsMatchIn(value)
            ) {
                throw MaestroSetupFailure("invalid_feature_variables")
            }
        }
    }
}
