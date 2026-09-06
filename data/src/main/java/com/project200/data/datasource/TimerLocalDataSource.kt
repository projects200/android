package com.project200.data.datasource

import androidx.room.withTransaction
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.dao.TimerDao
import com.project200.data.local.entity.CachedTimerStep
import com.project200.data.local.entity.CustomTimerEntity
import com.project200.data.local.entity.SimpleTimerEntity
import com.project200.data.local.entity.SyncState
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/** 서버 심플 타이머 한 행입니다. 서버 목록 반영에만 씁니다 */
data class ServerSimpleTimer(
    val serverId: Long,
    val time: Int,
)

/** 서버 커스텀 타이머 한 행입니다. 서버 목록 반영에만 씁니다 */
data class ServerCustomTimer(
    val serverId: Long,
    val name: String,
    val steps: List<CachedTimerStep>,
)

/**
 * 타이머를 읽고 씁니다. 기기가 원본이라 서버 응답 없이도 생성과 수정이 끝납니다.
 *
 * 계정 조건을 여기서 한 번에 겁니다. 호출부가 memberId를 넘기지 않게 해서 조건을
 * 빠뜨린 쿼리가 나올 자리를 없앱니다
 *
 * 상태 전이 규칙은 셋입니다.
 * 생성 대기 행을 고치면 생성 대기를 유지합니다. 수정 대기로 바꾸면 전송 작업이
 * 서버에 없는 항목에 수정 요청을 보냅니다
 * 생성 대기 행을 지우면 서버가 모르는 행이라 즉시 지웁니다
 * 서버에 있는 행을 지우면 삭제 대기로 표시만 합니다. 바로 지우면 서버에 알릴 방법이 없습니다
 */
class TimerLocalDataSource
    @Inject
    constructor(
        private val database: UndabangDatabase,
        private val timerDao: TimerDao,
        private val preferenceManager: PreferenceManager,
    ) {
        suspend fun getSimpleTimers(): List<SimpleTimerEntity> {
            val memberId = currentMemberId() ?: return emptyList()
            return timerDao.getSimpleTimers(memberId)
        }

        suspend fun countSimpleTimers(): Int {
            val memberId = currentMemberId() ?: return 0
            return timerDao.countSimpleTimers(memberId)
        }

        suspend fun createSimpleTimer(time: Int): String? {
            val memberId = currentMemberId() ?: return null
            val localId = newId()
            timerDao.upsertSimpleTimer(
                SimpleTimerEntity(
                    memberId = memberId,
                    localId = localId,
                    serverId = null,
                    syncState = SyncState.CREATE_PENDING,
                    time = time,
                    createRequestId = newId(),
                    pendingEditId = null,
                    editedAt = now(),
                ),
            )
            return localId
        }

        suspend fun updateSimpleTimer(
            localId: String,
            time: Int,
        ) {
            val memberId = currentMemberId() ?: return
            val current = timerDao.getSimpleTimer(memberId, localId) ?: return
            timerDao.upsertSimpleTimer(
                current.copy(
                    time = time,
                    syncState = nextStateOnEdit(current.syncState),
                    pendingEditId = newId(),
                    editedAt = now(),
                ),
            )
        }

        suspend fun deleteSimpleTimer(localId: String) {
            val memberId = currentMemberId() ?: return
            val current = timerDao.getSimpleTimer(memberId, localId) ?: return
            if (current.serverId == null) {
                timerDao.deleteSimpleTimer(memberId, localId)
                return
            }
            timerDao.upsertSimpleTimer(
                current.copy(
                    syncState = SyncState.DELETE_PENDING,
                    pendingEditId = newId(),
                    editedAt = now(),
                ),
            )
        }

        suspend fun getCustomTimers(): List<CustomTimerEntity> {
            val memberId = currentMemberId() ?: return emptyList()
            return timerDao.getCustomTimers(memberId)
        }

        suspend fun getCustomTimer(localId: String): CustomTimerEntity? {
            val memberId = currentMemberId() ?: return null
            return timerDao.getCustomTimer(memberId, localId)
        }

        suspend fun createCustomTimer(
            name: String,
            steps: List<CachedTimerStep>,
        ): String? {
            val memberId = currentMemberId() ?: return null
            val localId = newId()
            timerDao.upsertCustomTimer(
                CustomTimerEntity(
                    memberId = memberId,
                    localId = localId,
                    serverId = null,
                    syncState = SyncState.CREATE_PENDING,
                    name = name,
                    steps = steps,
                    createRequestId = newId(),
                    pendingEditId = null,
                    editedAt = now(),
                ),
            )
            return localId
        }

        suspend fun updateCustomTimer(
            localId: String,
            name: String,
            steps: List<CachedTimerStep>,
        ) {
            val memberId = currentMemberId() ?: return
            val current = timerDao.getCustomTimer(memberId, localId) ?: return
            timerDao.upsertCustomTimer(
                current.copy(
                    name = name,
                    steps = steps,
                    syncState = nextStateOnEdit(current.syncState),
                    pendingEditId = newId(),
                    editedAt = now(),
                ),
            )
        }

        suspend fun deleteCustomTimer(localId: String) {
            val memberId = currentMemberId() ?: return
            val current = timerDao.getCustomTimer(memberId, localId) ?: return
            if (current.serverId == null) {
                timerDao.deleteCustomTimer(memberId, localId)
                return
            }
            timerDao.upsertCustomTimer(
                current.copy(
                    syncState = SyncState.DELETE_PENDING,
                    pendingEditId = newId(),
                    editedAt = now(),
                ),
            )
        }

        suspend fun getPendingSimpleTimers(): List<SimpleTimerEntity> {
            val memberId = currentMemberId() ?: return emptyList()
            return timerDao.getPendingSimpleTimers(memberId)
        }

        suspend fun getPendingCustomTimers(): List<CustomTimerEntity> {
            val memberId = currentMemberId() ?: return emptyList()
            return timerDao.getPendingCustomTimers(memberId)
        }

        /**
         * 서버 심플 타이머 목록을 반영합니다.
         *
         * 대기 행이 걸린 서버ID는 건드리지 않고, 동기화 완료 행은 자리를 갱신하며 localId를
         * 이어 씁니다. 서버 목록에 없는 로컬 동기화 완료 행은 지웁니다
         */
        suspend fun replaceSyncedSimpleTimers(server: List<ServerSimpleTimer>) {
            val memberId = currentMemberId() ?: return
            database.withTransaction {
                val pendingServerIds = timerDao.getPendingSimpleTimerServerIds(memberId).toSet()
                val incoming = server.filterNot { it.serverId in pendingServerIds }
                val incomingServerIds = incoming.map { it.serverId }

                val reusableLocalIds =
                    if (incomingServerIds.isEmpty()) {
                        emptyMap()
                    } else {
                        timerDao.getSyncedSimpleTimersByServerIds(memberId, incomingServerIds)
                            .associate { it.serverId to it.localId }
                    }

                timerDao.deleteSyncedSimpleTimersNotIn(memberId, incomingServerIds)
                timerDao.upsertSimpleTimers(
                    incoming.map { serverTimer ->
                        SimpleTimerEntity(
                            memberId = memberId,
                            localId = reusableLocalIds[serverTimer.serverId] ?: newId(),
                            serverId = serverTimer.serverId,
                            syncState = SyncState.SYNCED,
                            time = serverTimer.time,
                            createRequestId = newId(),
                            pendingEditId = null,
                            editedAt = now(),
                        )
                    },
                )
            }
        }

        /**
         * 서버 커스텀 타이머 목록을 반영합니다.
         *
         * 대기 행이 걸린 서버ID는 건드리지 않고, 동기화 완료 행은 자리를 갱신하며 localId를
         * 이어 씁니다. 서버 목록에 없는 로컬 동기화 완료 행은 지웁니다
         */
        suspend fun replaceSyncedCustomTimers(server: List<ServerCustomTimer>) {
            val memberId = currentMemberId() ?: return
            database.withTransaction {
                val pendingServerIds = timerDao.getPendingCustomTimerServerIds(memberId).toSet()
                val incoming = server.filterNot { it.serverId in pendingServerIds }
                val incomingServerIds = incoming.map { it.serverId }

                val reusableLocalIds =
                    if (incomingServerIds.isEmpty()) {
                        emptyMap()
                    } else {
                        timerDao.getSyncedCustomTimersByServerIds(memberId, incomingServerIds)
                            .associate { it.serverId to it.localId }
                    }

                timerDao.deleteSyncedCustomTimersNotIn(memberId, incomingServerIds)
                timerDao.upsertCustomTimers(
                    incoming.map { serverTimer ->
                        CustomTimerEntity(
                            memberId = memberId,
                            localId = reusableLocalIds[serverTimer.serverId] ?: newId(),
                            serverId = serverTimer.serverId,
                            syncState = SyncState.SYNCED,
                            name = serverTimer.name,
                            steps = serverTimer.steps,
                            createRequestId = newId(),
                            pendingEditId = null,
                            editedAt = now(),
                        )
                    },
                )
            }
        }

        private fun nextStateOnEdit(current: SyncState): SyncState =
            if (current == SyncState.CREATE_PENDING) SyncState.CREATE_PENDING else SyncState.UPDATE_PENDING

        private fun newId(): String = UUID.randomUUID().toString()

        private fun now(): Long = System.currentTimeMillis()

        private fun currentMemberId(): String? {
            return preferenceManager.getMemberId().also {
                if (it == null) Timber.w("회원ID가 없어 타이머 저장을 건너뜁니다")
            }
        }
    }
