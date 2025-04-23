package com.project200.data.di

import com.project200.data.impl.OauthRepositoryImpl
import com.project200.domain.repository.OauthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    fun provideRepository(): OauthRepository = OauthRepositoryImpl()
}
