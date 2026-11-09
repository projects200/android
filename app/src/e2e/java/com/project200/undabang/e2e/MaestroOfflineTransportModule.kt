package com.project200.undabang.e2e

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.Interceptor
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MaestroOfflineTransportModule {
    @Binds
    @IntoSet
    @Named("exerciseSyncInterceptors")
    abstract fun bindOfflineTransport(transport: MaestroOfflineTransport): Interceptor
}
