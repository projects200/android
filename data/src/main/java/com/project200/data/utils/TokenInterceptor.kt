package com.project200.data.utils // 현재 인터셉터의 패키지 경로

import com.project200.undabang.oauth.AuthStateManager
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber // Timber 임포트 추가
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenInterceptor @Inject constructor(
    private val authStateManager: AuthStateManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestUrlString = originalRequest.url.toString()
        val currentAuthState = authStateManager.getCurrent()

        val requestBuilder = originalRequest.newBuilder() // 원본 요청을 기반으로 빌더 생성
        Timber.tag("TokenInterceptor").d("Intercepting URL: $requestUrlString")

        if (requestUrlString.contains("/auth/sign-up")) {
            //idToken 사용
            currentAuthState.idToken?.let { idToken ->
                requestBuilder.header("Authorization", "Bearer $idToken")
            } ?: run {
                Timber.w("ID Token is null")
            }
        } else {
            currentAuthState.accessToken?.let { accessToken ->
                Timber.tag("TokenInterceptor").d("accessToken: $accessToken")
                requestBuilder.header("Authorization", "Bearer $accessToken")
            } ?: run {
                Timber.w("Access Token is null")
            }
        }

        return chain.proceed(requestBuilder.build())
    }
}