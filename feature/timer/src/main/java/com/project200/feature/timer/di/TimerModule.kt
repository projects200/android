package com.project200.feature.timer.di

import com.project200.domain.manager.TimerManager
import com.project200.feature.timer.utils.CountDownTimerManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TimerModule {
    @Binds
    @Singleton
    abstract fun bindTimerManager(countDownTimerManager: CountDownTimerManager): TimerManager
}
