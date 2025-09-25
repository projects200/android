package com.project200.data.di

import com.project200.data.api.KakaoApiService
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
import javax.inject.Named
import javax.inject.Singleton

/**
 * Kakao API 관련 네트워크 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
object KakaoNetworkModule {

    @Provides
    @Singleton
    @Named("kakaoClient")
    fun provideKakaoOkHttpClient(
        @Named("kakao_rest_api_key") apiKey: String
    ): OkHttpClient {
        Timber.d("Kakao API Key being used: $apiKey")

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("Authorization", "KakaoAK $apiKey")
            val request = requestBuilder.build()
            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()
    }

    @Provides
    @Singleton
    @Named("kakao")
    fun provideKakaoRetrofit(
        @Named("kakaoClient") okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://dapi.kakao.com") // 카카오 로컬 API 베이스 URL
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideKakaoLocalApiService(@Named("kakao") retrofit: Retrofit): KakaoApiService {
        return retrofit.create(KakaoApiService::class.java)
    }
}