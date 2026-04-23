package com.project200.data.utils // 현재 인터셉터의 패키지 경로

import com.project200.undabang.oauth.AuthStateManager
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation
import timber.log.Timber // Timber 임포트 추가
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenInterceptor
    @Inject
    constructor(
        private val authStateManager: AuthStateManager,
        private val fcmTokenProvider: FcmTokenProvider,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()

            // HTTP 메서드(GET, POST 등)에 접근
            val httpMethod = originalRequest.method

            // Retrofit 어노테이션 정보를 가져오기
            val invocation = originalRequest.tag(Invocation::class.java)
            val method = invocation?.method()

            // 토큰 타입 결정
            val tokenType: TokenType =
                when {
                    method?.isAnnotationPresent(AccessTokenApi::class.java) == true -> TokenType.ACCESS
                    method?.isAnnotationPresent(AccessTokenWithFcmApi::class.java) == true -> TokenType.ACCESS_WITH_FCM
                    method?.isAnnotationPresent(IdTokenApi::class.java) == true -> TokenType.ID
                    else -> TokenType.ACCESS // 기본값으로 Access 토큰을 사용하도록 설정
                }

            // 토큰 타입에 따라 헤더 추가
            val requestBuilder = originalRequest.newBuilder()
            //
            // 주의: 새 Cognito 사용자 풀(2026-04 이후 생성)이 발급하는 V2 access token 은
            // API Gateway Cognito User Pool Authorizer 와 호환 문제가 있어 401 을 반환한다.
            // 따라서 ACCESS / ACCESS_WITH_FCM 도 실제로는 ID token 을 헤더에 실어 보낸다.
            // (백엔드는 sub/email 만 사용하므로 동작에 영향 없음)
            when (tokenType) {
                TokenType.ACCESS -> {
                    authStateManager.getCurrent().idToken?.let { idToken ->
                        requestBuilder.header("Authorization", "Bearer $idToken")
                    } ?: Timber.w("ID Token is null for $httpMethod ${originalRequest.url}")
                }
                TokenType.ID -> {
                    authStateManager.getCurrent().idToken?.let { idToken ->
                        requestBuilder.header("Authorization", "Bearer $idToken")
                    } ?: Timber.w("ID Token is null for $httpMethod ${originalRequest.url}")
                }
                TokenType.ACCESS_WITH_FCM -> {
                    authStateManager.getCurrent().idToken?.let { idToken ->
                        requestBuilder.header("Authorization", "Bearer $idToken")
                    } ?: Timber.w("ID Token is null for $httpMethod ${originalRequest.url}")
                    fcmTokenProvider.getFcmToken()?.let { fcmToken ->
                        requestBuilder.header("X-Fcm-Token", fcmToken)
                    }
                }
            }

            return chain.proceed(requestBuilder.build())
        }
    }

enum class TokenType {
    ACCESS,
    ID,
    ACCESS_WITH_FCM,
}
