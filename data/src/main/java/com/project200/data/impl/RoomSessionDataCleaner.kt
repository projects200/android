package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.datasource.ExerciseImageFileStore
import com.project200.data.local.UndabangDatabase
import com.project200.domain.manager.SessionDataCleaner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomSessionDataCleaner
    @Inject
    constructor(
        private val database: UndabangDatabase,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val imageFileStore: ExerciseImageFileStore,
    ) : SessionDataCleaner {
        override suspend fun clearAll() =
            withContext(ioDispatcher) {
                imageFileStore.withLock {
                    imageFileStore.invalidateSession()
                    database.clearAllTables()
                    imageFileStore.clearOwnedFiles()
                }
            }
    }
