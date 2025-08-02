package com.project200.data.utils // 현재 인터셉터의 패키지 경로

import com.project200.undabang.oauth.AuthStateManager
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation
import timber.log.Timber // Timber 임포트 추가
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenInterceptor @Inject constructor(
    private val authStateManager: AuthStateManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // HTTP 메서드(GET, POST 등)에 접근
        val httpMethod = originalRequest.method

        // Retrofit 어노테이션 정보를 가져오기
        val invocation = originalRequest.tag(Invocation::class.java)
        val method = invocation?.method()

        // 토큰 타입 결정
        val tokenType: TokenType = when {
            method?.isAnnotationPresent(AccessTokenApi::class.java) == true -> TokenType.ACCESS
            method?.isAnnotationPresent(IdTokenApi::class.java) == true -> TokenType.ID
            else -> TokenType.ACCESS // 기본값으로 Access 토큰을 사용하도록 설정
        }

        // 토큰 타입에 따라 헤더 추가
        val requestBuilder = originalRequest.newBuilder()
        when (tokenType) {
            TokenType.ACCESS -> {
                authStateManager.getCurrent().accessToken?.let { accessToken ->
                    requestBuilder.header("Authorization", "Bearer $accessToken")
                } ?: Timber.w("Access Token is null for $httpMethod ${originalRequest.url}")
            }
            TokenType.ID -> {
                authStateManager.getCurrent().idToken?.let { idToken ->
                    requestBuilder.header("Authorization", "Bearer $idToken")
                } ?: Timber.w("ID Token is null for $httpMethod ${originalRequest.url}")
            }
        }

        return chain.proceed(requestBuilder.build())
    }
}

enum class TokenType {
    ACCESS, ID
}