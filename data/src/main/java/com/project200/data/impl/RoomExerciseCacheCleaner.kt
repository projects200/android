package com.project200.data.impl

import androidx.room.withTransaction
import com.project200.common.di.IoDispatcher
import com.project200.common.utils.ClockProvider
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.dao.ExerciseCountDao
import com.project200.data.local.dao.ExerciseRecordDao
import com.project200.domain.manager.ExerciseCacheCleaner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomExerciseCacheCleaner
    @Inject
    constructor(
        private val database: UndabangDatabase,
        private val exerciseCountDao: ExerciseCountDao,
        private val exerciseRecordDao: ExerciseRecordDao,
        private val clockProvider: ClockProvider,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ExerciseCacheCleaner {
        override suspend fun cleanExpiredCache() =
            withContext(ioDispatcher) {
                val cutoff = clockProvider.now().minusMonths(6)
                val cutoffStart = cutoff.atStartOfDay()
                // 모든 회원의 캐시를 정리하되, 짝을 찾는 SQL은 같은 회원 안에서만 비교합니다
                database.withTransaction {
                    exerciseRecordDao.deleteExpiredSyncedDetails(cutoff, cutoffStart)
                    exerciseRecordDao.deleteExpiredSyncedListItems(cutoff, cutoffStart)
                    exerciseRecordDao.deleteExpiredQueryMarkers(cutoff)
                    exerciseCountDao.deleteBefore(cutoff)
                }
            }
    }
