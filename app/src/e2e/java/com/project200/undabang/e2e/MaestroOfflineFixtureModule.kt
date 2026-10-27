package com.project200.undabang.e2e

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MaestroOfflineFixtureModule {
    @Binds
    @IntoMap
    @StringKey("offline")
    abstract fun bindOfflineFixture(impl: MaestroOfflineFixtures): MaestroFeatureFixture
}
