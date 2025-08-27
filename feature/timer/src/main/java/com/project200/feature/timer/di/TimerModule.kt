package com.project200.feature.timer.di

import com.project200.feature.timer.utils.CountDownTimerManager
import com.project200.domain.manager.TimerManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
abstract class TimerModule {

    @Binds
    abstract fun bindTimerManager(
        countDownTimerManager: CountDownTimerManager
    ): TimerManager
}