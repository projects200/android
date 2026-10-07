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
                    inFlightEditId = null,
                    inFlightEditedAt = null,
                    inFlightOperation = null,
                    lastSyncError = null,
                    inFlightTime = null,
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
            // 전송 중인 생성은 서버에 행이 생길 수 있어 즉시 지우지 않습니다. 삭제 대기로 두면
            // 생성이 끝나 serverId가 채워진 뒤 삭제가 나갑니다
            if (current.serverId == null && current.inFlightOperation == null) {
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
                    inFlightEditId = null,
                    inFlightEditedAt = null,
                    inFlightOperation = null,
                    lastSyncError = null,
                    inFlightName = null,
                    inFlightSteps = null,
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
            // 심플 타이머와 같은 이유로 전송 중인 생성은 즉시 지우지 않습니다
            if (current.serverId == null && current.inFlightOperation == null) {
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
         * 전송을 시작합니다. 대기 행의 현재 값을 전송 중 칸으로 복사해 워커가 이 고정값으로
         * 서버에 보내게 합니다. 재시도 중인 행이면 다시 복사하지 않고 이전에 고정한 값을
         * 그대로 돌려줍니다. 이것이 재시도에도 같은 값을 보내는 핵심입니다
         *
         * 동기화 완료 행이거나 없는 행이면 보낼 것이 없어 null을 돌립니다
         */
        suspend fun startSimpleTimerSync(localId: String): SimpleTimerEntity? {
            val memberId = currentMemberId() ?: return null
            return database.withTransaction {
                val current = timerDao.getSimpleTimer(memberId, localId) ?: return@withTransaction null
                if (current.syncState == SyncState.SYNCED) return@withTransaction null
                if (current.inFlightOperation != null) return@withTransaction current

                val started =
                    current.copy(
                        inFlightEditId = current.pendingEditId,
                        inFlightEditedAt = current.editedAt,
                        inFlightOperation = current.syncState,
                        inFlightTime = current.time,
                        lastSyncError = null,
                    )
                timerDao.upsertSimpleTimer(started)
                started
            }
        }

        /**
         * 전송 성공을 반영합니다. serverId는 생성 성공일 때만 넘어옵니다.
         *
         * 전송 중에 pendingEditId가 그대로면 SYNCED로 바꾸고 전송 중 칸을 지웁니다.
         * 전송 중에 사용자가 다시 저장해 pendingEditId가 달라졌으면 이어서 보낼 편집이
         * 남은 것이라 syncState는 그대로 두고 전송 중 칸만 지웁니다. serverId는 두 경우
         * 모두 반영합니다. 생성 직후 다시 수정된 행은 이 갈래를 타면서 serverId가 새로 생겨
         * 더는 생성 대기가 아니므로 수정 대기로 올립니다. 그래야 다음 전송이 생성이 아니라
         * 수정으로 나갑니다
         */
        suspend fun finishSimpleTimerSync(
            localId: String,
            serverId: Long?,
        ) {
            val memberId = currentMemberId() ?: return
            database.withTransaction {
                val current = timerDao.getSimpleTimer(memberId, localId) ?: return@withTransaction
                if (current.inFlightOperation == SyncState.DELETE_PENDING) {
                    timerDao.deleteSimpleTimer(memberId, localId)
                    return@withTransaction
                }

                val withServerId = if (serverId != null) current.copy(serverId = serverId) else current
                val finished =
                    when {
                        withServerId.pendingEditId == withServerId.inFlightEditId ->
                            withServerId.copy(syncState = SyncState.SYNCED)
                        withServerId.syncState == SyncState.CREATE_PENDING && withServerId.serverId != null ->
                            withServerId.copy(syncState = SyncState.UPDATE_PENDING)
                        else -> withServerId
                    }
                timerDao.upsertSimpleTimer(
                    finished.copy(
                        inFlightEditId = null,
                        inFlightEditedAt = null,
                        inFlightOperation = null,
                        inFlightTime = null,
                        lastSyncError = null,
                    ),
                )
            }
        }

        /** 전송 실패를 기록합니다. 재시도가 고정값을 다시 보내야 하므로 전송 중 칸은 건드리지 않습니다 */
        suspend fun recordSimpleTimerSyncError(
            localId: String,
            error: String,
        ) {
            val memberId = currentMemberId() ?: return
            val current = timerDao.getSimpleTimer(memberId, localId) ?: return
            timerDao.upsertSimpleTimer(current.copy(lastSyncError = error))
        }

        /** 전송을 시작합니다. 심플 타이머와 규칙이 같습니다 */
        suspend fun startCustomTimerSync(localId: String): CustomTimerEntity? {
            val memberId = currentMemberId() ?: return null
            return database.withTransaction {
                val current = timerDao.getCustomTimer(memberId, localId) ?: return@withTransaction null
                if (current.syncState == SyncState.SYNCED) return@withTransaction null
                if (current.inFlightOperation != null) return@withTransaction current

                val started =
                    current.copy(
                        inFlightEditId = current.pendingEditId,
                        inFlightEditedAt = current.editedAt,
                        inFlightOperation = current.syncState,
                        inFlightName = current.name,
                        inFlightSteps = current.steps,
                        lastSyncError = null,
                    )
                timerDao.upsertCustomTimer(started)
                started
            }
        }

        /** 전송 성공을 반영합니다. 심플 타이머와 규칙이 같습니다 */
        suspend fun finishCustomTimerSync(
            localId: String,
            serverId: Long?,
        ) {
            val memberId = currentMemberId() ?: return
            database.withTransaction {
                val current = timerDao.getCustomTimer(memberId, localId) ?: return@withTransaction
                if (current.inFlightOperation == SyncState.DELETE_PENDING) {
                    timerDao.deleteCustomTimer(memberId, localId)
                    return@withTransaction
                }

                val withServerId = if (serverId != null) current.copy(serverId = serverId) else current
                val finished =
                    when {
                        withServerId.pendingEditId == withServerId.inFlightEditId ->
                            withServerId.copy(syncState = SyncState.SYNCED)
                        withServerId.syncState == SyncState.CREATE_PENDING && withServerId.serverId != null ->
                            withServerId.copy(syncState = SyncState.UPDATE_PENDING)
                        else -> withServerId
                    }
                timerDao.upsertCustomTimer(
                    finished.copy(
                        inFlightEditId = null,
                        inFlightEditedAt = null,
                        inFlightOperation = null,
                        inFlightName = null,
                        inFlightSteps = null,
                        lastSyncError = null,
                    ),
                )
            }
        }

        /** 전송 실패를 기록합니다. 심플 타이머와 규칙이 같습니다 */
        suspend fun recordCustomTimerSyncError(
            localId: String,
            error: String,
        ) {
            val memberId = currentMemberId() ?: return
            val current = timerDao.getCustomTimer(memberId, localId) ?: return
            timerDao.upsertCustomTimer(current.copy(lastSyncError = error))
        }

        /**
         * 서버 심플 타이머 목록을 반영합니다.
         *
         * 대기 행이 걸린 서버ID는 건드리지 않고, 동기화 완료 행은 자리를 갱신하며 localId를
         * 이어 씁니다. 서버 목록에 없는 로컬 동기화 완료 행은 지웁니다
         *
         * 서버 행을 잘라내지 않습니다. 판정 단위는 목록 전체가 아니라 타이머 하나이고,
         * 개수는 서버가 정합니다
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
                            inFlightEditId = null,
                            inFlightEditedAt = null,
                            inFlightOperation = null,
                            lastSyncError = null,
                            inFlightTime = null,
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
                            inFlightEditId = null,
                            inFlightEditedAt = null,
                            inFlightOperation = null,
                            lastSyncError = null,
                            inFlightName = null,
                            inFlightSteps = null,
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
