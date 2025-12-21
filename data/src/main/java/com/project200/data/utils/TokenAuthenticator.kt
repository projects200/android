// com.project200.data.utils.TokenAuthenticator.kt

package com.project200.data.utils

import com.project200.undabang.oauth.AuthManager
import com.project200.undabang.oauth.AuthStateManager
import com.project200.undabang.oauth.TokenRefreshResult
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Invocation
import timber.log.Timber
import javax.inject.Inject

/**
 * 이 클래스는 OkHttp의 Authenticator를 구현하여,
 * 서버로부터 401 Unauthorized 응답을 받았을 때, 자동으로 액세스 토큰을 갱신하고
 * 원래 요청을 재시도하는 역할을 합니다.
 * */
class TokenAuthenticator
    @Inject
    constructor(
        private val authStateManager: AuthStateManager,
        private val authManager: AuthManager,
    ) : Authenticator {
        override fun authenticate(
            route: Route?,
            response: Response,
        ): Request? {
            // 재시도한 요청(priorResponse가 있는)이 또 401 에러를 반환한 경우,
            // 토큰 갱신이 성공했음에도 권한이 없다는 의미이므로 무한 루프를 막기 위해 인증을 중단합니다.
            if (response.priorResponse != null) {
                Timber.tag("TokenAuthenticator").e("토큰 갱신 후 재시도했으나 또 401 에러 발생. 인증 절차를 중단합니다.")
                return null
            }

            // AccessTokenAndFcmTokenApi일 경우 건너뛰기
            val invocation = response.request.tag(Invocation::class.java)
            if (invocation?.method()?.isAnnotationPresent(AccessTokenWithFcmApi::class.java) == true) {
                Timber.tag("TokenAuthenticator").d("AccessTokenAndFcmTokenApi 요청, 인증 건너뜀")
                return null
            }

            // 이전에 사용된 토큰 (요청 실패한 토큰)
            val originalAccessToken = response.request.header("Authorization")?.substringAfter("Bearer ")

            // 동시에 여러 요청이 401을 받아 이 메소드로 진입하는 것을 방지하기 위한 동기화 블록
            synchronized(this) {
                val currentAccessToken = authStateManager.getCurrent().accessToken

                // 토큰이 다른 스레드에 의해 갱신되었는지 확인
                // 이전에 실패했던 토큰과 현재 저장된 토큰이 다르다면, 이미 토큰이 갱신 완료
                if (originalAccessToken != null && originalAccessToken != currentAccessToken) {
                    Timber.tag("TokenAuthenticator").d("이미 토큰 재발급완료")
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer $currentAccessToken")
                        .build()
                }

                // 토큰 갱신 시도
                Timber.tag("TokenAuthenticator").w("Access token expired. Attempting to refresh.")
                val refreshResult =
                    runBlocking {
                        authManager.refreshAccessToken()
                    }

                return when (refreshResult) {
                    is TokenRefreshResult.Success -> {
                        // 갱신을 했는데도 이전과 동일한 토큰을 받았다면, 문제가 해결되지 않은 것이므로 루프를 중단합니다.
                        if (originalAccessToken == refreshResult.tokenResponse.accessToken) {
                            Timber.tag("TokenAuthenticator").e("토큰 갱신 후에도 토큰이 동일하여 루프 방지를 위해 인증을 중단합니다.")
                            return null
                        }

                        // 토큰 갱신 성공: 새 토큰으로 새 요청 생성
                        Timber.tag("TokenAuthenticator").i("토큰 갱신 성공, api 요청 재시도")
                        response.request.newBuilder()
                            .header("Authorization", "Bearer ${refreshResult.tokenResponse.accessToken}")
                            .build()
                    }
                    else -> {
                        // 토큰 갱신 실패
                        Timber.tag("TokenAuthenticator").e("Token refresh failed. Cannot authenticate.")
                        null
                    }
                }
            }
        }
    }
