package com.project200.undabang.oauth.di

import android.content.Context
import com.project200.undabang.oauth.AuthManager
import com.project200.undabang.oauth.AuthStateManager
import com.project200.undabang.oauth.config.CognitoConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.openid.appauth.AuthorizationService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthCoreModule {
    @Provides
    @Singleton
    fun provideAuthStateManager(
        @ApplicationContext context: Context,
        cognitoConfig: CognitoConfig,
    ): AuthStateManager {
        return AuthStateManager(context, cognitoConfig)
    }

    @Provides
    @Singleton
    fun provideAuthorizationService(
        @ApplicationContext context: Context,
    ): AuthorizationService {
        return AuthorizationService(context)
    }

    @Provides
    @Singleton
    fun provideAuthManager(
        authService: AuthorizationService,
        authStateManager: AuthStateManager,
        cognitoConfig: CognitoConfig,
    ): AuthManager {
        return AuthManager(authService, authStateManager, cognitoConfig)
    }

    @Provides
    @Singleton
    fun provideCognitoConfig(): CognitoConfig {
        return CognitoConfig()
    }
}
