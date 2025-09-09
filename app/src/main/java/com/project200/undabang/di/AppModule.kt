package com.project200.undabang.di

import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.project200.common.utils.ClockProvider
import com.project200.common.utils.SystemClockProvider
import com.project200.undabang.BuildConfig
import com.project200.undabang.R
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig {
        val remoteConfig = Firebase.remoteConfig

        val configSettings =
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 60 else 3600
            }
        remoteConfig.setConfigSettingsAsync(configSettings)

        // Remote Config 기본값 설정
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)

        return remoteConfig
    }

    @Provides
    @Singleton
    fun provideClockProvider(systemClockProvider: SystemClockProvider): ClockProvider {
        return systemClockProvider
    }
}
