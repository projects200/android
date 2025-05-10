package com.project200.undabang.oauth.di

import android.content.Context
import com.project200.undabang.oauth.AuthManager
import com.project200.undabang.oauth.AuthStateManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthCoreModule {

    @Provides
    @Singleton
    fun provideAuthStateManager(@ApplicationContext context: Context): AuthStateManager {
        return AuthStateManager(context)
    }

    @Provides
    @Singleton
    fun provideAuthManager(
        authStateManager: AuthStateManager
        // ApplicationContext는 AuthStateManager를 통해 간접적으로 사용되므로 직접 주입 불필요
    ): AuthManager {
        return AuthManager(authStateManager)
    }
}