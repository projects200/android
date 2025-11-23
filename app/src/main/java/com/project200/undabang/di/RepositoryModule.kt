package com.project200.undabang.di

import com.project200.common.utils.ChatRoomStateRepository
import com.project200.undabang.fcm.ChatRoomStateRepositoryImpl
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
}
