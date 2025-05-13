package com.project200.undabang.oauth

import android.content.Intent
import androidx.core.net.toUri
import com.project200.undabang.core.oauth.BuildConfig
import com.project200.undabang.oauth.config.CognitoConfig.ISSUER_URI
import com.project200.undabang.oauth.config.CognitoConfig.REDIRECT_URI
import com.project200.undabang.oauth.config.CognitoConfig.SCOPES
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
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
            ).setScope(SCOPES)

            identityProvider?.let {
                val params = mutableMapOf<String, String>()
                params["identity_provider"] = it
                authRequestBuilder.setAdditionalParameters(params)
            }

            val authRequest = authRequestBuilder.build()
            Timber.tag(TAG).d("Auth Request: ${authRequest.jsonSerializeString()}")

            // Activity에서 authIntent를 받아 실행할 수 있도록 콜백으로 전달
            // authService는 Activity에서 전달받은 것을 사용
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

        // AuthStateManager의 현재 AuthState를 업데이트
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
        // authService는 Activity에서 전달받은 것을 사용
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

    companion object {
        private const val TAG = "AuthManager"
    }
}