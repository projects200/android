package com.project200.data.datasource

import androidx.room.withTransaction
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.dao.ExerciseCountDao
import com.project200.data.local.dao.ExerciseRecordDao
import com.project200.data.local.entity.CachedPicture
import com.project200.data.local.entity.ExerciseCacheQueryEntity
import com.project200.data.local.entity.ExerciseCountEntity
import com.project200.data.local.entity.ExerciseImageEntity
import com.project200.data.local.entity.ExerciseListItemEntity
import com.project200.data.local.entity.ExerciseRecordDetailEntity
import com.project200.data.local.entity.ExerciseRecoveryState
import com.project200.data.local.entity.ExerciseSyncEntity
import com.project200.data.local.entity.ExerciseSyncError
import com.project200.data.local.entity.SyncState
import com.project200.data.mapper.toEntity
import com.project200.data.mapper.toModel
import com.project200.data.mapper.toSyncedEntity
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseWriteInput
import com.project200.domain.model.LocalExerciseSaveResult
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

/** 계정별 일회성 Room 스냅샷입니다. 네트워크 요청 전에 잡은 계정으로만 반영합니다. */
class ExerciseRecordLocalDataSource
    @Inject
    constructor(
        private val database: UndabangDatabase,
        private val exerciseCountDao: ExerciseCountDao,
        private val exerciseRecordDao: ExerciseRecordDao,
        private val preferenceManager: PreferenceManager,
    ) {
        fun currentMemberId(): String? = preferenceManager.getMemberId()

        fun isCurrentMember(memberId: String?): Boolean = memberId != null && currentMemberId() == memberId

        suspend fun hasPendingServerRecord(
            serverId: Long,
            memberId: String,
        ): Boolean = serverId in exerciseRecordDao.getAllPendingServerIds(memberId)

        suspend fun isDeleted(
            recordId: String,
            serverId: Long?,
            memberId: String?,
        ): Boolean = isCurrentMember(memberId) && exerciseRecordDao.isDeleted(memberId!!, recordId, serverId)

        suspend fun markOnlineConflict(
            serverId: Long,
            memberId: String,
        ) {
            database.withTransaction {
                require(isCurrentMember(memberId)) { "ACCOUNT_MISMATCH" }
                if (database.exerciseSyncDao().markOnlineConflict(memberId, serverId) > 0) return@withTransaction
                val id = localId(memberId, serverId)
                val item = exerciseRecordDao.getListItem(memberId, id)
                val detail = exerciseRecordDao.getDetailByLocalId(memberId, id)
                val journal = desiredJournal(memberId, id, serverId, item, detail, null, item?.date ?: detail?.startedAt?.toLocalDate())
                database.exerciseSyncDao().saveDesired(
                    journal.copy(recoveryState = ExerciseRecoveryState.RECOVERY_REQUIRED, lastError = ExerciseSyncError.CONFLICT),
                )
            }
        }

        fun invalidateCache(memberId: String) {
            preferenceManager.setExerciseCacheInvalidated(memberId, true)
        }

        /** 전송 가능한 원문을 추정하지 않고 호출자가 준비한 저장 상태만 원자적으로 보존합니다 */
        suspend fun persistPending(
            journal: ExerciseSyncEntity,
            item: ExerciseListItemEntity,
            detail: ExerciseRecordDetailEntity,
            images: List<ExerciseImageEntity> = emptyList(),
        ) {
            require(isCurrentMember(journal.memberId)) { "ACCOUNT_MISMATCH" }
            require(item.memberId == journal.memberId && detail.memberId == journal.memberId) { "ACCOUNT_MISMATCH" }
            require(item.localId == journal.localId && detail.localId == journal.localId) { "RECORD_MISMATCH" }
            require(item.serverId == journal.serverId && detail.serverId == journal.serverId) { "SERVER_ID_MISMATCH" }
            require(item.syncState == journal.operation && detail.syncState == journal.operation) { "STATE_MISMATCH" }
            require(item.date == journal.currentDate) { "DATE_MISMATCH" }
            require(images.all { it.memberId == journal.memberId && it.recordLocalId == journal.localId }) { "IMAGE_IDENTITY_MISMATCH" }
            require(images.map { it.localId }.distinct().size == images.size) { "DUPLICATE_IMAGE_ID" }
            database.withTransaction {
                require(isCurrentMember(journal.memberId)) { "ACCOUNT_MISMATCH" }
                database.exerciseSyncDao().saveDesired(journal)
                exerciseRecordDao.upsertListItems(listOf(item))
                exerciseRecordDao.upsertDetail(detail)
                images.forEach { database.exerciseImageDao().save(it) }
            }
        }

        suspend fun createRecord(
            input: ExerciseWriteInput,
            memberId: String = requireNotNull(currentMemberId()) { "ACCOUNT_MISMATCH" },
        ): LocalExerciseSaveResult =
            database.withTransaction {
                require(isCurrentMember(memberId)) { "ACCOUNT_MISMATCH" }
                require(input.addedImageUris.isEmpty() && input.removedImageIds.isEmpty()) { "ONLINE_IMAGES_REQUIRED" }
                val id = newLocalId()
                val journal =
                    ExerciseSyncEntity(
                        memberId = memberId,
                        localId = id,
                        serverId = null,
                        operation = SyncState.CREATE_PENDING,
                        revision = 1L,
                        operationId = newLocalId(),
                        createRequestId = newLocalId(),
                        serverVersion = null,
                        confirmedDate = null,
                        currentDate = input.record.startedAt.toLocalDate(),
                        recoveryState = ExerciseRecoveryState.READY,
                    )
                saveLocalRecord(journal, input.record, null)
                LocalExerciseSaveResult(id)
            }

        suspend fun updateRecord(
            localId: String,
            input: ExerciseWriteInput,
            memberId: String = requireNotNull(currentMemberId()) { "ACCOUNT_MISMATCH" },
        ): LocalExerciseSaveResult =
            database.withTransaction {
                require(isCurrentMember(memberId)) { "ACCOUNT_MISMATCH" }
                require(input.addedImageUris.isEmpty() && input.removedImageIds.isEmpty()) { "ONLINE_IMAGES_REQUIRED" }
                val detail = requireNotNull(exerciseRecordDao.getDetailByLocalId(memberId, localId)) { "INCOMPLETE_DETAIL" }
                require(!exerciseRecordDao.isDeleted(memberId, localId, detail.serverId)) { "RECORD_DELETED" }
                val item = exerciseRecordDao.getListItem(memberId, localId)
                val previous = database.exerciseSyncDao().get(memberId, localId)
                val date =
                    if (input.record.startedAt.toLocalDate() == detail.startedAt.toLocalDate()) {
                        item?.date ?: previous?.currentDate ?: detail.startedAt.toLocalDate()
                    } else {
                        input.record.startedAt.toLocalDate()
                    }
                val serverId = checkedServerId(item, detail, previous)
                val journal = desiredJournal(memberId, localId, serverId, item, detail, previous, date)
                saveLocalRecord(journal, input.record, item, detail.pictures)
                LocalExerciseSaveResult(localId)
            }

        suspend fun deleteRecord(
            localId: String,
            memberId: String = requireNotNull(currentMemberId()) { "ACCOUNT_MISMATCH" },
        ) {
            database.withTransaction {
                require(isCurrentMember(memberId)) { "ACCOUNT_MISMATCH" }
                val item = exerciseRecordDao.getListItem(memberId, localId)
                val detail = exerciseRecordDao.getDetailByLocalId(memberId, localId)
                val previous = database.exerciseSyncDao().get(memberId, localId)
                require(item != null || detail != null || previous != null) { "RECORD_NOT_FOUND" }
                if (previous?.operation == SyncState.DELETE_PENDING) return@withTransaction
                val serverId = checkedServerId(item, detail, previous)
                val date = item?.date ?: previous?.currentDate ?: detail?.startedAt?.toLocalDate()
                date?.let { markLocalDate(memberId, it) }
                val images = database.exerciseImageDao().getByRecord(memberId, localId)
                val definitelyUnsent =
                    serverId == null && previous?.operation == SyncState.CREATE_PENDING &&
                        previous.recoveryState == ExerciseRecoveryState.READY && previous.lastError == null &&
                        previous.revision != null && !previous.operationId.isNullOrBlank() &&
                        !previous.createRequestId.isNullOrBlank() && !hasInFlight(previous) &&
                        (item == null || item.syncState == SyncState.CREATE_PENDING) &&
                        (detail == null || detail.syncState == SyncState.CREATE_PENDING) && images.isEmpty()
                if (definitelyUnsent) {
                    check(
                        database.exerciseSyncDao().deleteMatching(
                            memberId,
                            localId,
                            previous?.operationId,
                            previous?.revision,
                        ) == 1,
                    ) { "JOURNAL_CHANGED" }
                    exerciseRecordDao.deleteUnsentListItem(memberId, localId)
                    exerciseRecordDao.deleteUnsentDetail(memberId, localId)
                } else {
                    val journal = desiredJournal(memberId, localId, serverId, item, detail, previous, date)
                    val unresolvedCreate =
                        serverId == null && (previous == null || !hasInFlight(previous) || hasIncompleteInFlight(previous))
                    database.exerciseSyncDao().saveDesired(
                        journal.copy(
                            operation = SyncState.DELETE_PENDING,
                            recoveryState = if (unresolvedCreate) ExerciseRecoveryState.RECOVERY_REQUIRED else journal.recoveryState,
                            lastError = journal.lastError ?: if (unresolvedCreate) ExerciseSyncError.LEGACY_PENDING else null,
                        ),
                    )
                    item?.let { exerciseRecordDao.upsertListItems(listOf(it.copy(syncState = SyncState.DELETE_PENDING))) }
                    detail?.let { exerciseRecordDao.upsertDetail(it.copy(syncState = SyncState.DELETE_PENDING)) }
                }
            }
        }

        private fun checkedServerId(
            item: ExerciseListItemEntity?,
            detail: ExerciseRecordDetailEntity?,
            previous: ExerciseSyncEntity?,
        ): Long? {
            require(item == null || detail == null || item.serverId == detail.serverId) { "SERVER_ID_MISMATCH" }
            require(previous == null || item == null || previous.serverId == item.serverId) { "SERVER_ID_MISMATCH" }
            require(previous == null || detail == null || previous.serverId == detail.serverId) { "SERVER_ID_MISMATCH" }
            return previous?.serverId ?: item?.serverId ?: detail?.serverId
        }

        private fun hasInFlight(journal: ExerciseSyncEntity): Boolean =
            journal.inFlightOperation != null || journal.inFlightRevision != null || journal.inFlightOperationId != null ||
                journal.inFlightRequestId != null || journal.inFlightBody != null || journal.inFlightBaseVersion != null

        private fun hasIncompleteInFlight(journal: ExerciseSyncEntity): Boolean =
            hasInFlight(journal) &&
                (
                    journal.inFlightOperation == null || journal.inFlightRevision == null ||
                        journal.inFlightOperationId.isNullOrBlank() || journal.inFlightRequestId.isNullOrBlank() ||
                        journal.inFlightBody.isNullOrBlank() ||
                        (journal.inFlightOperation != SyncState.CREATE_PENDING && journal.inFlightBaseVersion == null)
                )

        private fun desiredJournal(
            memberId: String,
            localId: String,
            serverId: Long?,
            item: ExerciseListItemEntity?,
            detail: ExerciseRecordDetailEntity?,
            previous: ExerciseSyncEntity?,
            date: LocalDate?,
        ): ExerciseSyncEntity {
            // 실제 본문 대기의 알 수 없는 기준은 보존하고 SYNCED 복구 표식만 명시적 투영 기준을 이어받습니다
            val pendingBody = previous != null && previous.operation != SyncState.SYNCED
            val confirmedDate =
                if (pendingBody) previous?.confirmedDate else previous?.confirmedDate ?: item?.confirmedDate ?: detail?.confirmedDate
            val version =
                if (pendingBody) previous?.serverVersion else previous?.serverVersion ?: item?.serverVersion ?: detail?.serverVersion
            val operation =
                when {
                    previous?.operation == SyncState.CREATE_PENDING -> SyncState.CREATE_PENDING
                    previous?.operation == SyncState.UPDATE_PENDING -> SyncState.UPDATE_PENDING
                    item?.syncState == SyncState.UPDATE_PENDING || detail?.syncState == SyncState.UPDATE_PENDING ->
                        SyncState.UPDATE_PENDING
                    serverId == null -> SyncState.CREATE_PENDING
                    else -> SyncState.UPDATE_PENDING
                }
            val contradictory =
                (operation == SyncState.CREATE_PENDING && serverId != null) ||
                    (operation == SyncState.UPDATE_PENDING && serverId == null)
            val rawPending =
                (item != null && item.syncState != SyncState.SYNCED) || (detail != null && detail.syncState != SyncState.SYNCED)
            val legacy =
                (previous == null && rawPending) || (serverId == null && previous?.createRequestId.isNullOrBlank())
            val recovery =
                legacy || contradictory || date == null || (serverId != null && (confirmedDate == null || version == null)) ||
                    previous?.recoveryState == ExerciseRecoveryState.RECOVERY_REQUIRED ||
                    (previous != null && hasIncompleteInFlight(previous))
            return ExerciseSyncEntity(
                memberId = memberId,
                localId = localId,
                serverId = serverId,
                operation = operation,
                revision = if (legacy && previous?.revision == null) null else (previous?.revision ?: 0L) + 1L,
                operationId = if (legacy && previous?.operationId == null) null else newLocalId(),
                createRequestId = previous?.createRequestId,
                serverVersion = version,
                confirmedDate = confirmedDate,
                currentDate = date,
                recoveryState =
                    if (recovery) ExerciseRecoveryState.RECOVERY_REQUIRED else previous?.recoveryState ?: ExerciseRecoveryState.READY,
                lastError =
                    previous?.lastError
                        ?: when {
                            contradictory -> ExerciseSyncError.CONTRADICTORY_STATE
                            recovery -> ExerciseSyncError.LEGACY_PENDING
                            else -> null
                        },
            )
        }

        private suspend fun saveLocalRecord(
            journal: ExerciseSyncEntity,
            record: ExerciseRecord,
            previous: ExerciseListItemEntity?,
            previousPictures: List<CachedPicture>? = null,
        ) {
            val date = requireNotNull(journal.currentDate)
            val pictures = previousPictures
            persistPending(
                journal,
                ExerciseListItemEntity(
                    memberId = journal.memberId,
                    localId = journal.localId,
                    serverId = journal.serverId,
                    syncState = journal.operation,
                    date = date,
                    sortOrder = previous?.sortOrder ?: 0,
                    title = record.title,
                    personalType = record.personalType,
                    startedAt = record.startedAt,
                    endedAt = record.endedAt,
                    thumbnailUrls = pictures?.map { it.url } ?: previous?.thumbnailUrls,
                    serverVersion = journal.serverVersion,
                    confirmedDate = journal.confirmedDate,
                ),
                ExerciseRecordDetailEntity(
                    memberId = journal.memberId,
                    localId = journal.localId,
                    serverId = journal.serverId,
                    syncState = journal.operation,
                    title = record.title,
                    detail = record.detail,
                    personalType = record.personalType,
                    startedAt = record.startedAt,
                    endedAt = record.endedAt,
                    location = record.location,
                    pictures = pictures,
                    serverVersion = journal.serverVersion,
                    confirmedDate = journal.confirmedDate,
                ),
            )
            markLocalDate(journal.memberId, date)
            previous?.date?.takeIf { it != date }?.let { markLocalDate(journal.memberId, it) }
        }

        private suspend fun markLocalDate(
            memberId: String,
            date: LocalDate,
        ) {
            exerciseRecordDao.saveQuery(ExerciseCacheQueryEntity(memberId, "list", date))
            exerciseRecordDao.saveQuery(ExerciseCacheQueryEntity(memberId, "count", date))
        }

        /** 무효 표시를 지우기 전에 동기화 완료 캐시와 빈 조회 메타데이터를 함께 버립니다. */
        suspend fun prepareCache(memberId: String?) {
            if (memberId == null || !isCurrentMember(memberId) || !preferenceManager.isExerciseCacheInvalidated(memberId)) return
            database.withTransaction {
                if (!isCurrentMember(memberId)) return@withTransaction
                exerciseRecordDao.deleteSyncedList(memberId)
                exerciseRecordDao.deleteSyncedDetails(memberId)
                exerciseCountDao.deleteByMember(memberId)
                exerciseRecordDao.deleteQueries(memberId)
                database.exerciseImageDao().deleteOrphanedRemoteCache(memberId)
            }
            preferenceManager.setExerciseCacheInvalidated(memberId, false)
        }

        suspend fun getCountsByRange(
            startDate: LocalDate,
            endDate: LocalDate,
            memberId: String? = currentMemberId(),
        ): List<ExerciseCount> {
            if (!isCurrentMember(memberId)) return emptyList()
            return database.withTransaction {
                val counts = exerciseCountDao.getRange(memberId!!, startDate, endDate).associate { it.date to it.count }
                val journals = database.exerciseSyncDao().getByMember(memberId)
                val journalIds = journals.map { it.localId }.toSet()
                val pending = exerciseRecordDao.getPendingInRange(memberId, startDate, endDate).groupBy { it.date }
                val deltas = mutableMapOf<LocalDate, Int>()
                val pendingDates = mutableSetOf<LocalDate>()

                fun add(
                    date: LocalDate?,
                    delta: Int,
                ) {
                    if (date == null) return
                    deltas[date] = (deltas[date] ?: 0) + delta
                    pendingDates.add(date)
                }

                journals.filter { it.operation != SyncState.SYNCED }.forEach { journal ->
                    when (journal.operation) {
                        SyncState.CREATE_PENDING -> add(journal.currentDate, 1)
                        SyncState.UPDATE_PENDING -> {
                            // 기준을 모르면 UPDATE를 CREATE로 세지 않습니다
                            if (journal.confirmedDate != null) {
                                add(journal.confirmedDate, -1)
                                add(journal.currentDate, 1)
                            } else {
                                add(journal.currentDate, 0)
                            }
                        }
                        SyncState.DELETE_PENDING -> {
                            add(journal.confirmedDate, -1)
                            add(journal.currentDate, 0)
                        }
                        SyncState.SYNCED -> Unit
                    }
                }
                val dates = (counts.keys + pending.keys + pendingDates).filter { it in startDate..endDate }.sorted()
                dates.map { date ->
                    val rows = pending[date].orEmpty()
                    val legacy = rows.filterNot { it.localId in journalIds }
                    val delta =
                        (deltas[date] ?: 0) + legacy.count { it.syncState == SyncState.CREATE_PENDING && it.serverId == null } -
                            legacy.count { it.syncState == SyncState.DELETE_PENDING && it.confirmedDate == date }
                    val visible = rows.count { it.syncState != SyncState.DELETE_PENDING }
                    val count = maxOf((counts[date] ?: 0) + delta, visible).coerceAtLeast(0)
                    ExerciseCount(date, count, date in pendingDates || rows.any { it.syncState != SyncState.DELETE_PENDING })
                }
            }
        }

        suspend fun getCountsSnapshot(
            startDate: LocalDate,
            endDate: LocalDate,
            memberId: String?,
        ): List<ExerciseCount>? {
            if (!isCurrentMember(memberId)) return null
            return database.withTransaction {
                val counts = getCountsByRange(startDate, endDate, memberId)
                val expectedDays = ChronoUnit.DAYS.between(startDate, endDate) + 1
                val hasVisibleRecords = counts.any { it.count > 0 || it.syncPending }
                if (hasVisibleRecords || exerciseRecordDao.hasCountRange(memberId!!, startDate, endDate, expectedDays)) counts else null
            }
        }

        suspend fun replaceCountsByRange(
            startDate: LocalDate,
            endDate: LocalDate,
            counts: List<ExerciseCount>,
            memberId: String? = currentMemberId(),
        ) {
            if (!isCurrentMember(memberId)) return
            database.withTransaction {
                if (!isCurrentMember(memberId)) return@withTransaction
                exerciseCountDao.deleteRange(memberId!!, startDate, endDate)
                exerciseCountDao.upsertAll(counts.filter { it.date in startDate..endDate }.map { it.toEntity(memberId) })
                var date = startDate
                while (!date.isAfter(endDate)) {
                    exerciseRecordDao.saveQuery(ExerciseCacheQueryEntity(memberId, "count", date))
                    date = date.plusDays(1)
                }
            }
        }

        suspend fun getListByDate(
            date: LocalDate,
            memberId: String? = currentMemberId(),
        ): List<ExerciseListItem> {
            if (!isCurrentMember(memberId)) return emptyList()
            return database.withTransaction {
                exerciseRecordDao.getListByDate(memberId!!, date)
                    .filterNot { exerciseRecordDao.isDeleted(memberId, it.localId, it.serverId) }
                    .map { it.toModel() }
            }
        }

        suspend fun getListSnapshot(
            date: LocalDate,
            memberId: String?,
        ): List<ExerciseListItem>? {
            if (!isCurrentMember(memberId)) return null
            return database.withTransaction {
                val items = getListByDate(date, memberId)
                if (items.isNotEmpty() || exerciseRecordDao.hasQuery(memberId!!, "list", date)) items else null
            }
        }

        /** items는 원격 매퍼의 서버 ID 문자열입니다. 저장한 뒤 읽는 값은 localId로 바뀝니다. */
        suspend fun replaceSyncedListByDate(
            date: LocalDate,
            items: List<ExerciseListItem>,
            memberId: String? = currentMemberId(),
        ) {
            if (!isCurrentMember(memberId)) return
            database.withTransaction {
                if (!isCurrentMember(memberId)) return@withTransaction
                val pendingServerIds = exerciseRecordDao.getAllPendingServerIds(memberId!!).toSet()
                val incoming = items.withIndex().filterNot { it.value.recordId.toLong() in pendingServerIds }
                val incomingServerIds = incoming.map { it.value.recordId.toLong() }
                val reusableItems =
                    if (incomingServerIds.isEmpty()) {
                        emptyMap()
                    } else {
                        exerciseRecordDao.getSyncedByServerIds(memberId, incomingServerIds).associateBy { it.serverId }
                    }
                val existing = exerciseRecordDao.getSyncedByDate(memberId, date).filterNot { it.serverId in pendingServerIds }
                val removedLocalIds = existing.filterNot { it.serverId in incomingServerIds }.map { it.localId }
                existing.forEach { exerciseRecordDao.deleteListItem(memberId, it.localId) }
                if (removedLocalIds.isNotEmpty()) exerciseRecordDao.deleteDetailsByLocalIds(memberId, removedLocalIds)
                if (incomingServerIds.isNotEmpty()) exerciseRecordDao.deleteSyncedOnOtherDates(memberId, date, incomingServerIds)
                exerciseRecordDao.upsertListItems(
                    incoming.mapNotNull { (sortOrder, item) ->
                        val previous = reusableItems[item.recordId.toLong()]
                        val id =
                            previous?.localId
                                ?: exerciseRecordDao.findDetailLocalId(memberId, item.recordId.toLong())
                                ?: newLocalId()
                        if (exerciseRecordDao.hasPendingRecord(memberId, id)) return@mapNotNull null
                        val oldDetail =
                            exerciseRecordDao.getDetailByLocalId(memberId, id)
                                ?: exerciseRecordDao.getDetailByServerId(memberId, item.recordId.toLong())
                        exerciseRecordDao.updateConfirmedDate(memberId, id, date)
                        item.toSyncedEntity(memberId, id, date, sortOrder).copy(
                            serverVersion = previous?.serverVersion ?: oldDetail?.serverVersion,
                            confirmedDate = date,
                        )
                    },
                )
                exerciseRecordDao.saveQuery(ExerciseCacheQueryEntity(memberId, "list", date))
                database.exerciseImageDao().deleteOrphanedRemoteCache(memberId)
            }
        }

        /** 로컬 쓰기는 현재 계정에 이미 저장된 ID만 사용하고 매핑이 없으면 생성하지 않습니다 */
        suspend fun resolveExistingLocalId(
            recordId: String,
            memberId: String,
        ): String? {
            require(isCurrentMember(memberId)) { "ACCOUNT_MISMATCH" }
            val serverId = recordId.toLongOrNull()?.takeIf { it > 0 }
            if (serverId != null) {
                return exerciseRecordDao.findListItemLocalId(memberId, serverId)
                    ?: exerciseRecordDao.findDetailLocalId(memberId, serverId)
                    ?: database.exerciseSyncDao().findLocalId(memberId, serverId)
            }
            return recordId.takeIf {
                exerciseRecordDao.getListItem(memberId, it) != null ||
                    exerciseRecordDao.getDetailByLocalId(memberId, it) != null ||
                    database.exerciseSyncDao().get(memberId, it) != null
            }
        }

        suspend fun resolveServerId(
            recordId: String,
            memberId: String?,
        ): Long? {
            if (!isCurrentMember(memberId)) return null
            val detail = exerciseRecordDao.getDetailByLocalId(memberId!!, recordId)
            val item = exerciseRecordDao.getListItem(memberId, recordId)
            if (detail != null || item != null) return detail?.serverId ?: item?.serverId
            // 서버 응답으로 막 생성한 ID도 캐시 저장 전에는 이 경계로 들어올 수 있습니다.
            return recordId.toLongOrNull()?.takeIf { it > 0 }
        }

        suspend fun getDetailByServerId(
            serverId: Long,
            memberId: String? = currentMemberId(),
        ): ExerciseRecord? {
            if (!isCurrentMember(memberId)) return null
            return database.withTransaction {
                if (exerciseRecordDao.isDeleted(memberId!!, "", serverId)) return@withTransaction null
                exerciseRecordDao.getDetailByServerId(memberId, serverId)?.toModel()
            }
        }

        suspend fun getDetailByLocalId(
            localId: String,
            memberId: String? = currentMemberId(),
        ): ExerciseRecord? {
            if (!isCurrentMember(memberId)) return null
            return database.withTransaction {
                val detail = exerciseRecordDao.getDetailByLocalId(memberId!!, localId) ?: return@withTransaction null
                if (exerciseRecordDao.isDeleted(memberId, localId, detail.serverId)) return@withTransaction null
                detail.toModel()
            }
        }

        suspend fun saveSyncedDetail(
            serverId: Long,
            record: ExerciseRecord,
            memberId: String? = currentMemberId(),
            previousLocalId: String? = null,
        ): String? {
            if (!isCurrentMember(memberId)) return null
            return database.withTransaction {
                if (!isCurrentMember(memberId)) return@withTransaction null
                val localId =
                    exerciseRecordDao.findListItemLocalId(memberId!!, serverId)
                        ?: exerciseRecordDao.findDetailLocalId(memberId, serverId)
                        ?: previousLocalId
                        ?: newLocalId()
                if (
                    serverId !in exerciseRecordDao.getAllPendingServerIds(memberId) &&
                    !exerciseRecordDao.hasPendingRecord(memberId, localId)
                ) {
                    saveRemoteDetail(memberId, localId, serverId, record)
                }
                localId
            }
        }

        /** 쓰기 성공 후 목록·상세·집계를 같은 트랜잭션으로 맞춥니다. 날짜 이동도 이전 날짜에서 뺍니다. */
        suspend fun reconcileRecord(
            serverId: Long,
            record: ExerciseRecord,
            memberId: String?,
            bodyChanged: Boolean = false,
            newRecord: Boolean = false,
        ): String? {
            if (!isCurrentMember(memberId)) return null
            return database.withTransaction {
                if (!isCurrentMember(memberId)) return@withTransaction null
                val id = localId(memberId!!, serverId)
                if (exerciseRecordDao.hasPendingRecord(memberId, id) || serverId in exerciseRecordDao.getAllPendingServerIds(memberId)) {
                    database.exerciseSyncDao().markOnlineConflict(memberId, serverId)
                    return@withTransaction id
                }
                val oldItem = exerciseRecordDao.getListItem(memberId, id)
                val oldDetail = exerciseRecordDao.getDetailByServerId(memberId, serverId)
                val oldDate = oldItem?.date ?: oldDetail?.startedAt?.toLocalDate()
                val date = record.startedAt.toLocalDate()
                saveRemoteDetail(memberId, id, serverId, record, bodyChanged)
                exerciseRecordDao.upsertListItems(
                    listOf(
                        ExerciseListItemEntity(
                            memberId,
                            id,
                            serverId,
                            SyncState.SYNCED,
                            date,
                            oldItem?.sortOrder ?: 0,
                            record.title,
                            record.personalType,
                            record.startedAt,
                            record.endedAt,
                            record.pictures?.map { it.url },
                            serverVersion = if (bodyChanged) null else oldItem?.serverVersion ?: oldDetail?.serverVersion,
                            confirmedDate = if (bodyChanged) null else oldItem?.confirmedDate ?: oldDetail?.confirmedDate,
                        ),
                    ),
                )
                if (newRecord) {
                    adjustCount(memberId, date, 1)
                } else if (bodyChanged && oldDate != null && oldDate != date) {
                    adjustCount(memberId, oldDate, -1)
                    adjustCount(memberId, date, 1)
                }
                id
            }
        }

        /** 업로드 응답에는 URL이 없습니다. 후속 상세 조회가 실패하면 오래된 이미지를 내보내지 않습니다. */
        suspend fun invalidateImages(
            serverId: Long,
            memberId: String?,
        ) {
            if (!isCurrentMember(memberId)) return
            database.withTransaction {
                if (!isCurrentMember(memberId)) return@withTransaction
                if (serverId in exerciseRecordDao.getAllPendingServerIds(memberId!!)) return@withTransaction
                val id =
                    exerciseRecordDao.findListItemLocalId(memberId, serverId)
                        ?: exerciseRecordDao.findDetailLocalId(memberId, serverId)
                        ?: return@withTransaction
                if (exerciseRecordDao.isProtected(memberId, id)) return@withTransaction
                exerciseRecordDao.deleteDetail(memberId, id)
                database.exerciseImageDao().refreshSynced(memberId, id, null)
                exerciseRecordDao.getListItem(memberId, id)?.let {
                    exerciseRecordDao.upsertListItems(listOf(it.copy(thumbnailUrls = null)))
                }
            }
        }

        private suspend fun saveRemoteDetail(
            memberId: String,
            localId: String,
            serverId: Long,
            record: ExerciseRecord,
            bodyChanged: Boolean = false,
        ) {
            val oldDetail =
                exerciseRecordDao.getDetailByLocalId(memberId, localId)
                    ?: exerciseRecordDao.getDetailByServerId(memberId, serverId)
            val item = exerciseRecordDao.getListItem(memberId, localId)
            val entity =
                record.toSyncedEntity(memberId, localId, serverId).copy(
                    serverVersion = if (bodyChanged) null else oldDetail?.serverVersion ?: item?.serverVersion,
                    confirmedDate = if (bodyChanged) null else item?.confirmedDate ?: oldDetail?.confirmedDate,
                )
            exerciseRecordDao.upsertDetail(entity)
            database.exerciseImageDao().refreshSynced(memberId, localId, entity.pictures)
        }

        private suspend fun localId(
            memberId: String,
            serverId: Long,
        ): String =
            exerciseRecordDao.findListItemLocalId(memberId, serverId)
                ?: exerciseRecordDao.findDetailLocalId(memberId, serverId)
                ?: newLocalId()

        private suspend fun adjustCount(
            memberId: String,
            date: LocalDate,
            delta: Int,
        ) {
            val count = exerciseCountDao.getRange(memberId, date, date).singleOrNull()?.count ?: 0
            exerciseCountDao.upsertAll(listOf(ExerciseCountEntity(memberId, date, (count + delta).coerceAtLeast(0))))
        }

        private fun newLocalId(): String = UUID.randomUUID().toString()
    }
