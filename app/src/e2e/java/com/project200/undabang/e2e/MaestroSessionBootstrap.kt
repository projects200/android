package com.project200.undabang.e2e

import android.net.Uri
import com.project200.domain.repository.AuthRepository
import com.project200.undabang.oauth.AuthManager
import com.project200.undabang.oauth.AuthStateManager
import com.project200.undabang.oauth.TokenRefreshResult
import com.project200.undabang.oauth.config.CognitoConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.project200.undabang.core.oauth.BuildConfig as OAuthBuildConfig
import com.project200.undabang.data.BuildConfig as DataBuildConfig

internal class MaestroSessionBootstrap
    @Inject
    constructor(
        private val cognitoConfig: CognitoConfig,
        private val authStateManager: AuthStateManager,
        private val authManager: AuthManager,
        private val authRepository: AuthRepository,
        private val registrationCheck: MaestroRegistrationCheck,
    ) {
        suspend fun authenticate(
            state: AuthState,
            expectedMemberId: String? = null,
        ): String {
            val trusted = fetchTrustedConfiguration()
            validateState(state, trusted)
            val authorizationResponse = state.lastAuthorizationResponse ?: throw MaestroSetupFailure("untrusted_session")
            val restoredTokens = tokensForRestore(state)

            authRepository.clearSession()
            authStateManager.update(authorizationResponse, null)
            authStateManager.update(restoredTokens, null)

            return refreshAndVerify(expectedMemberId)
        }

        /** 실패한 오프라인 실행의 회원 경계와 미전송 행을 유지한 채 같은 계정만 복구합니다 */
        suspend fun authenticateForOfflineCleanup(
            state: AuthState,
            expectedMemberId: String,
        ): String {
            requireLocalMember(expectedMemberId)
            val trusted = fetchTrustedConfiguration()
            validateState(state, trusted)
            if (state.refreshToken.isNullOrBlank()) throw MaestroSetupFailure("untrusted_session")
            val current = currentState()
            if (current.isAuthorized && !current.refreshToken.isNullOrBlank()) {
                validateState(current, trusted)
            } else {
                val authorizationResponse = state.lastAuthorizationResponse ?: throw MaestroSetupFailure("untrusted_session")
                val restoredTokens = tokensForRestore(state)
                authRepository.clearTokens()
                authStateManager.update(authorizationResponse, null)
                authStateManager.update(restoredTokens, null)
            }
            return refreshAndVerifySameMember(expectedMemberId)
        }

        /** 서버 관찰은 현재 세션을 검증하되 Room과 미전송 변경을 지우지 않습니다 */
        suspend fun verifyCurrent(expectedMemberId: String): String {
            requireLocalMember(expectedMemberId)
            val trusted = fetchTrustedConfiguration()
            validateState(currentState(), trusted)
            return refreshAndVerifySameMember(expectedMemberId)
        }

        suspend fun verifyLocalCurrent(expectedMemberId: String) {
            requireLocalMember(expectedMemberId)
            val state = currentState()
            if (!state.isAuthorized || state.refreshToken.isNullOrBlank()) {
                throw MaestroSetupFailure("untrusted_session")
            }
        }

        private suspend fun refresh() {
            // 만료 시각이나 가져온 ID 토큰을 믿지 않고 실제 DEV 서버에서 새 토큰을 발급받습니다
            if (authManager.refreshAccessToken() !is TokenRefreshResult.Success) {
                throw MaestroSetupFailure("refresh_failed")
            }
            val current = authStateManager.getCurrent()
            if (!current.isAuthorized || current.idToken.isNullOrBlank() || current.refreshToken.isNullOrBlank()) {
                throw MaestroSetupFailure("refresh_failed")
            }
        }

        private suspend fun refreshAndVerify(expectedMemberId: String?): String {
            refresh()
            val memberId = registrationCheck.requireRegistered()
            if (expectedMemberId != null && memberId != expectedMemberId) {
                throw MaestroSetupFailure("member_mismatch")
            }
            return memberId
        }

        private suspend fun refreshAndVerifySameMember(expectedMemberId: String): String {
            refresh()
            // 관찰·복구의 등록 조회는 회원 저장이나 Room 삭제를 실행하지 않습니다.
            val memberId = registrationCheck.readRegisteredMember()
            if (memberId != expectedMemberId) throw MaestroSetupFailure("member_mismatch")
            requireLocalMember(expectedMemberId)
            return memberId
        }

        private suspend fun requireLocalMember(expectedMemberId: String) {
            if (expectedMemberId.isBlank() || authRepository.getMemberId() != expectedMemberId) {
                throw MaestroSetupFailure("member_mismatch")
            }
        }

        private fun tokensForRestore(state: AuthState): TokenResponse {
            val tokenResponse = state.lastTokenResponse ?: throw MaestroSetupFailure("untrusted_session")
            val refreshToken = state.refreshToken?.takeIf { it.isNotBlank() } ?: throw MaestroSetupFailure("untrusted_session")
            // AppAuth는 마지막 응답과 별도로 최신 refreshToken을 저장하므로 응답의 이전 값을 쓰지 않습니다
            return TokenResponse.Builder(tokenResponse.request)
                .setTokenType(tokenResponse.tokenType)
                .setAccessToken(tokenResponse.accessToken)
                .setAccessTokenExpirationTime(tokenResponse.accessTokenExpirationTime)
                .setIdToken(tokenResponse.idToken)
                .setRefreshToken(refreshToken)
                .setScope(state.scope ?: tokenResponse.scope)
                .setAdditionalParameters(tokenResponse.additionalParameters)
                .build()
        }

        fun currentState(): AuthState = authStateManager.getCurrent()

        suspend fun clearSession() {
            authRepository.clearSession()
            val state = authStateManager.getCurrent()
            if (state.isAuthorized || state.refreshToken != null || authRepository.getMemberId() != null) {
                throw MaestroSetupFailure("local_session_clear_failed")
            }
        }

        suspend fun clearTokens() {
            authRepository.clearTokens()
            val state = authStateManager.getCurrent()
            if (state.isAuthorized || state.refreshToken != null) {
                throw MaestroSetupFailure("local_session_clear_failed")
            }
        }

        private suspend fun fetchTrustedConfiguration(): AuthorizationServiceConfiguration {
            if (!OAuthBuildConfig.DEBUG || !DataBuildConfig.DEBUG ||
                cognitoConfig.cognitoRegion.isBlank() || cognitoConfig.cognitoUserPoolId.isBlank() ||
                cognitoConfig.cognitoAppClientId.isBlank()
            ) {
                throw MaestroSetupFailure("dev_configuration_missing")
            }
            val issuer = Uri.parse(cognitoConfig.issuerUri)
            if (!isHttpsEndpoint(issuer)) throw MaestroSetupFailure("dev_configuration_missing")

            val trusted =
                suspendCancellableCoroutine<AuthorizationServiceConfiguration> { continuation ->
                    AuthorizationServiceConfiguration.fetchFromIssuer(issuer) { configuration, exception ->
                        if (!continuation.isActive) return@fetchFromIssuer
                        if (exception != null || configuration == null) {
                            continuation.resumeWithException(MaestroSetupFailure("discovery_failed"))
                        } else {
                            continuation.resume(configuration)
                        }
                    }
                }
            // AppAuth의 discovery 조회 자체는 issuer 일치를 검사하지 않으므로 별도로 확인합니다
            if (trusted.discoveryDoc?.issuer != cognitoConfig.issuerUri ||
                !isHttpsEndpoint(trusted.authorizationEndpoint) || !isHttpsEndpoint(trusted.tokenEndpoint)
            ) {
                throw MaestroSetupFailure("discovery_failed")
            }
            return trusted
        }

        private fun validateState(
            state: AuthState,
            trusted: AuthorizationServiceConfiguration,
        ) {
            val authorization = state.lastAuthorizationResponse ?: throw MaestroSetupFailure("untrusted_session")
            val token = state.lastTokenResponse ?: throw MaestroSetupFailure("untrusted_session")
            if (state.authorizationException != null || state.lastRegistrationResponse != null ||
                authorization.request.clientId != cognitoConfig.cognitoAppClientId ||
                token.request.clientId != cognitoConfig.cognitoAppClientId ||
                authorization.request.redirectUri.toString() != cognitoConfig.redirectUri ||
                authorization.request.responseType != ResponseTypeValues.CODE ||
                authorization.authorizationCode.isNullOrBlank()
            ) {
                throw MaestroSetupFailure("untrusted_session")
            }
            validateConfiguration(authorization.request.configuration, trusted)
            validateConfiguration(token.request.configuration, trusted)
            val configuration = state.authorizationServiceConfiguration ?: throw MaestroSetupFailure("untrusted_session")
            validateConfiguration(configuration, trusted)
        }

        private fun validateConfiguration(
            imported: AuthorizationServiceConfiguration,
            trusted: AuthorizationServiceConfiguration,
        ) {
            if (imported.discoveryDoc?.issuer != cognitoConfig.issuerUri ||
                imported.authorizationEndpoint != trusted.authorizationEndpoint ||
                imported.tokenEndpoint != trusted.tokenEndpoint ||
                imported.registrationEndpoint != trusted.registrationEndpoint ||
                imported.endSessionEndpoint != trusted.endSessionEndpoint ||
                imported.discoveryDoc?.jwksUri != trusted.discoveryDoc?.jwksUri
            ) {
                throw MaestroSetupFailure("untrusted_session")
            }
        }

        private fun isHttpsEndpoint(uri: Uri): Boolean =
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
    }
