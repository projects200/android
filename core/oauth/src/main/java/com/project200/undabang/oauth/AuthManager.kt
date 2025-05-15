package com.project200.undabang.oauth

import android.content.Intent
import androidx.core.net.toUri
import com.project200.undabang.core.oauth.BuildConfig
import com.project200.undabang.oauth.config.CognitoConfig.ISSUER_URI
import com.project200.undabang.oauth.config.CognitoConfig.LOGOUT_REDIRECT_URI
import com.project200.undabang.oauth.config.CognitoConfig.REDIRECT_URI
import com.project200.undabang.oauth.config.CognitoConfig.SCOPES
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface AuthResultCallback {
    fun onAuthFlowStarted(authIntent: Intent)
    fun onSuccess(tokenResponse: TokenResponse)
    fun onError(exception: AuthorizationException?)
    fun onConfigurationError(exception: Exception)
}

interface LogoutResultCallback {
    fun onLogoutPageIntentReady(logoutIntent: Intent)
    fun onLocalLogoutCompleted()
    fun onLogoutProcessError(exception: Exception)
}

sealed class TokenRefreshResult {
    data class Success(val tokenResponse: TokenResponse) : TokenRefreshResult()
    data class Error(val exception: AuthorizationException?) : TokenRefreshResult()
    object NoRefreshToken : TokenRefreshResult() // 리프레시 토큰이 없을 경우
    object ConfigError : TokenRefreshResult() // 서비스 설정 로드 실패 등
}

@Singleton
class AuthManager @Inject constructor(
    private val authStateManager: AuthStateManager
) {

    private suspend fun fetchServiceConfiguration(): AuthorizationServiceConfiguration {
        return suspendCancellableCoroutine { continuation ->
            AuthorizationServiceConfiguration.fetchFromIssuer(
                ISSUER_URI.toUri(),
                { serviceConfiguration, ex ->
                    if (ex != null) {
                        Timber.tag(TAG).e(ex, "Failed to fetch configuration: ${ex.errorDescription}")
                        continuation.resumeWithException(ex)
                    } else if (serviceConfiguration != null) {
                        Timber.tag(TAG).i("Fetched configuration successfully.")
                        continuation.resume(serviceConfiguration)
                    } else {
                        Timber.tag(TAG).e("Fetched configuration is null without exception.")
                        continuation.resumeWithException(RuntimeException("Configuration is null"))
                    }
                }
            )
        }
    }

    suspend fun initiateAuthorization(
        authService: AuthorizationService,
        identityProvider: String? = null,
        callback: AuthResultCallback
    ) {
        try {
            val serviceConfig = fetchServiceConfiguration()
            Timber.tag(TAG).i("Fetched configuration: ${serviceConfig.toJsonString()}")

            val authRequestBuilder = AuthorizationRequest.Builder(
                serviceConfig,
                BuildConfig.COGNITO_APP_CLIENT_ID,
                ResponseTypeValues.CODE,
                REDIRECT_URI.toUri()
            ).setScope(SCOPES).setPromptValues(AuthorizationRequest.Prompt.SELECT_ACCOUNT)

            identityProvider?.let {
                val params = mutableMapOf<String, String>()
                params["identity_provider"] = it
                authRequestBuilder.setAdditionalParameters(params)
            }

            val authRequest = authRequestBuilder.build()
            Timber.tag(TAG).d("Auth Request: ${authRequest.jsonSerializeString()}")

            // Activity에서 authIntent를 받아 실행할 수 있도록 콜백으로 전달 용
            val authIntent = authService.getAuthorizationRequestIntent(authRequest)
            callback.onAuthFlowStarted(authIntent)

        } catch (ex: Exception) {
            if (ex is CancellationException) { throw ex }
            Timber.tag(TAG).e(ex, "Error during authorization initiation")
            callback.onConfigurationError(ex)
        }
    }

    fun handleAuthorizationResponse(
        authService: AuthorizationService,
        intent: Intent,
        callback: AuthResultCallback
    ) {
        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)

        authStateManager.update(response, exception)

        if (response != null) {
            Timber.tag(TAG).i("Authorization successful. Code: ${response.authorizationCode}")
            exchangeAuthorizationCodeForTokens(authService, response, callback)
        } else {
            Timber.tag(TAG).e(exception, "Authorization failed: ${exception?.errorDescription}")
            callback.onError(exception)
        }
    }

    private fun exchangeAuthorizationCodeForTokens(
        authService: AuthorizationService,
        response: AuthorizationResponse,
        callback: AuthResultCallback
    ) {
        authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, ex ->
            authStateManager.update(tokenResponse, ex)

            if (tokenResponse != null) {
                Timber.tag(TAG).i("Access Token: ${tokenResponse.accessToken}")
                callback.onSuccess(tokenResponse)
            } else {
                Timber.tag(TAG).e(ex, "Token exchange failed: ${ex?.errorDescription}")
                callback.onError(ex)
            }
        }
    }

    suspend fun logout(
        authService: AuthorizationService,
        callback: LogoutResultCallback
    ) {
        Timber.tag(TAG_DEBUG).d("Initiating logout.")
        var localLogoutAttempted = false
        try {
            val serviceConfig = fetchServiceConfiguration()
            val currentAuthState = authStateManager.getCurrent()
            val idTokenHint = currentAuthState.idToken

            if (idTokenHint == null) {
                Timber.tag(TAG_DEBUG).w("ID Token hint is null. Clearing local state only.")
                authStateManager.clearAuthState()
                localLogoutAttempted = true
                callback.onLocalLogoutCompleted()
                return
            }

            if (serviceConfig.endSessionEndpoint == null) {
                Timber.tag(TAG_DEBUG).e("End session endpoint is null. Clearing local state only.")
                authStateManager.clearAuthState()
                localLogoutAttempted = true
                callback.onLocalLogoutCompleted()
                callback.onLogoutProcessError(IllegalStateException("End session endpoint is not configured for $ISSUER_URI"))
                return
            }

            // 로컬 상태 먼저 정리
            authStateManager.clearAuthState()
            localLogoutAttempted = true
            Timber.tag(TAG_DEBUG).i("Local AuthState cleared for logout.")
            callback.onLocalLogoutCompleted()


            val manualLogoutUriBuilder = serviceConfig.endSessionEndpoint!!.buildUpon()
                .appendQueryParameter("client_id", BuildConfig.COGNITO_APP_CLIENT_ID)
                .appendQueryParameter("logout_uri", LOGOUT_REDIRECT_URI)
                .appendQueryParameter("id_token_hint", idTokenHint)

            val manualCognitoLogoutUri = manualLogoutUriBuilder.build()
            Timber.tag(TAG_DEBUG).d("Attempting to launch MANUALLY CONSTRUCTED browser intent with URI: $manualCognitoLogoutUri")

            if (manualCognitoLogoutUri != null) {
                val directBrowserIntent = Intent(Intent.ACTION_VIEW, manualCognitoLogoutUri)
                callback.onLogoutPageIntentReady(directBrowserIntent)
            } else {
                Timber.tag(TAG_DEBUG).e("Manually constructed CognitoLogoutUri is null.")
                val endSessionRequest = EndSessionRequest.Builder(serviceConfig)
                    .setIdTokenHint(idTokenHint)
                    .setPostLogoutRedirectUri(LOGOUT_REDIRECT_URI.toUri())
                    .setAdditionalParameters(mapOf("client_id" to BuildConfig.COGNITO_APP_CLIENT_ID))
                    .build()
                val endSessionIntentFromAppAuth = authService.getEndSessionRequestIntent(endSessionRequest)
                callback.onLogoutPageIntentReady(endSessionIntentFromAppAuth)
            }

        } catch (ex: Exception) {
            Timber.tag(TAG_DEBUG).e(ex, "Error during logout process.")
            if (ex is CancellationException) { throw ex }
            if (!localLogoutAttempted) {
                try {
                    authStateManager.clearAuthState()
                    callback.onLocalLogoutCompleted()
                } catch (clearEx: Exception) {
                    Timber.tag(TAG_DEBUG).e(clearEx, "Error clearing AuthState during logout error handling.")
                }
            }
            callback.onLogoutProcessError(ex)
        }
    }

    suspend fun refreshAccessToken(authService: AuthorizationService): TokenRefreshResult {
        Timber.tag(TAG_DEBUG).d("Attempting to refresh access token.")
        val currentState = authStateManager.getCurrent()
        val refreshToken = currentState.refreshToken

        if (refreshToken == null) {
            Timber.tag(TAG_DEBUG).w("No refresh token available.")
            return TokenRefreshResult.NoRefreshToken
        }

        // 서비스 설정은 AuthState에 이미 저장되어 있어야 함 (최초 인증 성공 시)
        val serviceConfig = currentState.lastAuthorizationResponse?.request?.configuration
        if (serviceConfig == null) {
            Timber.tag(TAG_DEBUG).e("ServiceConfiguration is missing in AuthState. Cannot refresh token.")
            return TokenRefreshResult.ConfigError
        }

        val tokenRefreshRequest = currentState.createTokenRefreshRequest()

        return suspendCancellableCoroutine { continuation ->
            authService.performTokenRequest(tokenRefreshRequest) { tokenResponse, ex ->
                authStateManager.update(tokenResponse, ex) // 새 토큰 또는 에러로 AuthState 업데이트
                if (continuation.isCancelled) return@performTokenRequest

                if (tokenResponse != null) {
                    Timber.tag(TAG_DEBUG).i("Access token refreshed successfully.")
                    continuation.resume(TokenRefreshResult.Success(tokenResponse))
                } else {
                    Timber.tag(TAG_DEBUG).e(ex, "Token refresh failed: ${ex?.errorDescription}")
                    // invalid_grant 등의 에러 발생 시, authStateManager에서 로컬 상태를 clear 할 수 있음
                    if (ex?.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR && ex.error == "invalid_grant") {
                        Timber.tag(TAG_DEBUG).w("Refresh token is invalid (invalid_grant). Clearing local AuthState.")
                        authStateManager.clearAuthState() // 리프레시 토큰이 무효하므로 로컬 상태 삭제
                    }
                    continuation.resume(TokenRefreshResult.Error(ex))
                }
            }
        }
    }

    companion object {
        private const val TAG = "AuthManager"
        private const val TAG_DEBUG = "AuthManagerDebug"
    }
}