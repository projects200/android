package com.project200.data.di

import com.project200.data.impl.AddressRepositoryImpl
import com.project200.data.impl.AppUpdateRepositoryImpl
import com.project200.data.impl.AuthRepositoryImpl
import com.project200.data.impl.ChattingRepositoryImpl
import com.project200.data.impl.ExerciseRecordRepositoryImpl
import com.project200.data.impl.FcmRepositoryImpl
import com.project200.data.impl.MatchingRepositoryImpl
import com.project200.data.impl.MemberRepositoryImpl
import com.project200.data.impl.PolicyRepositoryImpl
import com.project200.data.impl.ScoreRepositoryImpl
import com.project200.data.impl.TimerRepositoryImpl
import com.project200.domain.repository.AddressRepository
import com.project200.domain.repository.AppUpdateRepository
import com.project200.domain.repository.AuthRepository
import com.project200.domain.repository.ChattingRepository
import com.project200.domain.repository.ExerciseRecordRepository
import com.project200.domain.repository.FcmRepository
import com.project200.domain.repository.MatchingRepository
import com.project200.domain.repository.MemberRepository
import com.project200.domain.repository.PolicyRepository
import com.project200.domain.repository.ScoreRepository
import com.project200.domain.repository.TimerRepository
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
    abstract fun bindAppUpdateRepository(appUpdateRepositoryImpl: AppUpdateRepositoryImpl): AppUpdateRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(authRepositoryImpl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindExerciseRecordRepository(exerciseRecordRepositoryImpl: ExerciseRecordRepositoryImpl): ExerciseRecordRepository

    @Binds
    @Singleton
    abstract fun bindMemberRepository(memberRepositoryImpl: MemberRepositoryImpl): MemberRepository

    @Binds
    @Singleton
    abstract fun bindPolicyRepository(policyRepositoryImpl: PolicyRepositoryImpl): PolicyRepository

    @Binds
    @Singleton
    abstract fun bindScoreRepository(scoreRepositoryImpl: ScoreRepositoryImpl): ScoreRepository

    @Binds
    @Singleton
    abstract fun bindFcmRepository(fcmRepositoryImpl: FcmRepositoryImpl): FcmRepository

    @Binds
    @Singleton
    abstract fun bindTimerRepository(timerRepository: TimerRepositoryImpl): TimerRepository

    @Binds
    @Singleton
    abstract fun bindMatchingRepository(matchingRepositoryImpl: MatchingRepositoryImpl): MatchingRepository

    @Binds
    @Singleton
    abstract fun bindAddressRepository(addressRepositoryImpl: AddressRepositoryImpl): AddressRepository

    @Binds
    @Singleton
    abstract fun bindChattingRepository(chattingRepositoryImpl: ChattingRepositoryImpl): ChattingRepository
}
