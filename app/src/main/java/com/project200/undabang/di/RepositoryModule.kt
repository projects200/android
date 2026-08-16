package com.project200.undabang.di

import com.project200.common.utils.ChatRoomStateRepository
import com.project200.domain.manager.FcmTokenSyncScheduler
import com.project200.undabang.fcm.ChatRoomStateRepositoryImpl
import com.project200.undabang.fcm.FcmTokenSyncSchedulerImpl
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
    abstract fun bindChatRoomStateRepository(impl: ChatRoomStateRepositoryImpl): ChatRoomStateRepository

    @Binds
    @Singleton
    abstract fun bindFcmTokenSyncScheduler(impl: FcmTokenSyncSchedulerImpl): FcmTokenSyncScheduler
}
