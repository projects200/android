package com.project200.data.di

import com.project200.data.impl.AppUpdateRepositoryImpl
import com.project200.data.impl.AuthRepositoryImpl
import com.project200.data.impl.ExerciseRecordRepositoryImpl
import com.project200.domain.repository.AppUpdateRepository
import com.project200.domain.repository.AuthRepository
import com.project200.domain.repository.ExerciseRecordRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(
        appUpdateRepositoryImpl: AppUpdateRepositoryImpl
    ): AppUpdateRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindExerciseRecordRepository(
        exerciseRecordRepositoryImpl: ExerciseRecordRepositoryImpl
    ): ExerciseRecordRepository
}
