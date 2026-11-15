package com.project200.data.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import okhttp3.Interceptor
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
abstract class ExerciseSyncInterceptorModule {
    @Multibinds
    @Named("exerciseSyncInterceptors")
    abstract fun exerciseSyncInterceptors(): Set<Interceptor>
}
