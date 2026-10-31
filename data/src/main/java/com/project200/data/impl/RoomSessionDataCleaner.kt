package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.datasource.ExerciseImageFileStore
import com.project200.data.local.UndabangDatabase
import com.project200.domain.manager.SessionDataCleaner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
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

        override suspend fun hasStoredData(): Boolean = withContext(ioDispatcher) {
            // Room 메타데이터가 아닌 실제 사용자 테이블만 확인하며 회원 소유자를 추정하지 않습니다.
            database.openHelper.readableDatabase.query(
                "SELECT EXISTS(SELECT 1 FROM exercise_count) " +
                    "OR EXISTS(SELECT 1 FROM exercise_list_item) " +
                    "OR EXISTS(SELECT 1 FROM exercise_record_detail) " +
                    "OR EXISTS(SELECT 1 FROM exercise_cache_query) " +
                    "OR EXISTS(SELECT 1 FROM exercise_sync) " +
                    "OR EXISTS(SELECT 1 FROM exercise_image) " +
                    "OR EXISTS(SELECT 1 FROM simple_timer) " +
                    "OR EXISTS(SELECT 1 FROM custom_timer) " +
                    "OR EXISTS(SELECT 1 FROM timer_snapshot)",
            ).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0) != 0
            } || imageFileStore.hasOwnedFiles()
        }

        override suspend fun clearForAccountAdoption(
            discardApproved: Boolean,
            isCurrent: () -> Boolean,
            onCleared: () -> Unit,
        ): Boolean =
            withContext(ioDispatcher) {
                imageFileStore.withLock {
                    if (!isCurrent()) return@withLock false
                    // 계정 확정을 시작하면 취소로 DB·파일·계정 확정 사이를 끊지 않습니다.
                    withContext(NonCancellable) commit@{
                        if (!isCurrent()) return@commit false
                        if (!discardApproved && hasStoredData()) return@commit false
                        if (!isCurrent()) return@commit false
                        try {
                            // 빈 최초 로그인에는 삭제 권한이 없습니다. 승인이 있을 때만 실제로 지웁니다.
                            if (discardApproved) {
                                database.clearAllTables()
                                imageFileStore.clearOwnedFiles()
                            }
                            if (!isCurrent()) return@commit false
                        } finally {
                            imageFileStore.invalidateSession()
                        }
                        onCleared()
                        true
                    }
                }
            }
    }
