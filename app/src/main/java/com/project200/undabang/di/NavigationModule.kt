package com.project200.undabang.di

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.project200.presentation.navigator.ActivityNavigator
import com.project200.undabang.auth.login.LoginActivity
import com.project200.undabang.main.MainActivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NavigationModule {

    @Provides
    @Singleton
    fun provideAppNavigator(): ActivityNavigator {
        return object : ActivityNavigator {
            override fun navigateToMain(context: Context) {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                context.startActivity(intent)
            }

            override fun navigateToLogin(context: Context) {
                val intent = Intent(context, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                context.startActivity(intent)
            }

            override fun navigateToWeb(context: Context, url: String) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
        }
    }
}