package com.project200.undabang.di

import com.project200.undabang.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApiKeysModule {
    @Provides
    @Singleton
    @Named("kakao_rest_api_key")
    fun provideKakaoRestApiKey(): String = BuildConfig.KAKAO_REST_API_KEY
}
