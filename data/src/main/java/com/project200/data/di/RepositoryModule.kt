package com.project200.data.di

import com.project200.data.impl.AppUpdateRepositoryImpl
import com.project200.data.impl.AuthRepositoryImpl
import com.project200.data.impl.ExerciseRecordRepositoryImpl
import com.project200.data.impl.FcmRepositoryImpl
import com.project200.data.impl.MemberRepositoryImpl
import com.project200.data.impl.PolicyRepositoryImpl
import com.project200.data.impl.ScoreRepositoryImpl
import com.project200.domain.repository.AppUpdateRepository
import com.project200.domain.repository.AuthRepository
import com.project200.domain.repository.ExerciseRecordRepository
import com.project200.domain.repository.FcmRepository
import com.project200.domain.repository.MemberRepository
import com.project200.domain.repository.PolicyRepository
import com.project200.domain.repository.ScoreRepository
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

    @Binds
    @Singleton
    abstract fun bindMemberRepository(
        memberRepositoryImpl: MemberRepositoryImpl
    ): MemberRepository

    @Binds
    @Singleton
    abstract fun bindPolicyRepository(
        policyRepositoryImpl: PolicyRepositoryImpl
    ): PolicyRepository


    @Binds
    @Singleton
    abstract fun bindScoreRepository(
        scoreRepositoryImpl: ScoreRepositoryImpl
    ): ScoreRepository

    @Binds
    @Singleton
    abstract fun bindFcmRepository(
        fcmRepositoryImpl: FcmRepositoryImpl
    ): FcmRepository
}
