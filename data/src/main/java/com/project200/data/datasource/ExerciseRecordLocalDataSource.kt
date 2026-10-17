package com.project200.data.datasource

import androidx.room.withTransaction
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.dao.ExerciseCountDao
import com.project200.data.local.dao.ExerciseRecordDao
import com.project200.data.local.entity.ExerciseCacheQueryEntity
import com.project200.data.local.entity.ExerciseCountEntity
import com.project200.data.local.entity.ExerciseListItemEntity
import com.project200.data.local.entity.SyncState
import com.project200.data.mapper.toEntity
import com.project200.data.mapper.toModel
import com.project200.data.mapper.toSyncedEntity
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
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

        fun invalidateCache(memberId: String) {
            preferenceManager.setExerciseCacheInvalidated(memberId, true)
        }

        /** 무효 표시를 지우기 전에 동기화 완료 캐시와 빈 조회 메타데이터를 함께 버립니다. */
        suspend fun prepareCache(memberId: String?) {
            if (memberId == null || !isCurrentMember(memberId) || !preferenceManager.isExerciseCacheInvalidated(memberId)) return
            database.withTransaction {
                exerciseRecordDao.deleteSyncedList(memberId)
                exerciseRecordDao.deleteSyncedDetails(memberId)
                exerciseCountDao.deleteByMember(memberId)
                exerciseRecordDao.deleteQueries(memberId)
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
                val pending = exerciseRecordDao.getPendingInRange(memberId, startDate, endDate).groupBy { it.date }
                (counts.keys + pending.keys).sorted().map { date ->
                    val rows = pending[date].orEmpty()
                    val delta = rows.count { it.syncState == SyncState.CREATE_PENDING } - rows.count { it.syncState == SyncState.DELETE_PENDING }
                    val count = maxOf((counts[date] ?: 0) + delta, rows.count { it.syncState != SyncState.DELETE_PENDING })
                    ExerciseCount(date, count.coerceAtLeast(0), rows.any { it.syncState != SyncState.DELETE_PENDING })
                }
            }
        }

        suspend fun getCountsSnapshot(startDate: LocalDate, endDate: LocalDate, memberId: String?): List<ExerciseCount>? {
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

        suspend fun getListByDate(date: LocalDate, memberId: String? = currentMemberId()): List<ExerciseListItem> {
            if (!isCurrentMember(memberId)) return emptyList()
            return exerciseRecordDao.getListByDate(memberId!!, date).map { it.toModel() }
        }

        suspend fun getListSnapshot(date: LocalDate, memberId: String?): List<ExerciseListItem>? {
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
                val reusableLocalIds = if (incomingServerIds.isEmpty()) emptyMap() else {
                    exerciseRecordDao.getSyncedByServerIds(memberId, incomingServerIds).associate { it.serverId to it.localId }
                }
                val existing = exerciseRecordDao.getSyncedByDate(memberId, date).filterNot { it.serverId in pendingServerIds }
                val removedLocalIds = existing.filterNot { it.serverId in incomingServerIds }.map { it.localId }
                existing.forEach { exerciseRecordDao.deleteListItem(memberId, it.localId) }
                if (removedLocalIds.isNotEmpty()) exerciseRecordDao.deleteDetailsByLocalIds(memberId, removedLocalIds)
                if (incomingServerIds.isNotEmpty()) exerciseRecordDao.deleteSyncedOnOtherDates(memberId, date, incomingServerIds)
                exerciseRecordDao.upsertListItems(
                    incoming.map { (sortOrder, item) ->
                        val id = reusableLocalIds[item.recordId.toLong()]
                            ?: exerciseRecordDao.findDetailLocalId(memberId, item.recordId.toLong())
                            ?: newLocalId()
                        item.toSyncedEntity(memberId, id, date, sortOrder)
                    },
                )
                exerciseRecordDao.saveQuery(ExerciseCacheQueryEntity(memberId, "list", date))
            }
        }

        suspend fun resolveServerId(recordId: String, memberId: String?): Long? {
            if (!isCurrentMember(memberId)) return null
            val detail = exerciseRecordDao.getDetailByLocalId(memberId!!, recordId)
            val item = exerciseRecordDao.getListItem(memberId, recordId)
            if (detail != null || item != null) return detail?.serverId ?: item?.serverId
            // 서버 응답으로 막 생성한 ID도 캐시 저장 전에는 이 경계로 들어올 수 있습니다.
            return recordId.toLongOrNull()?.takeIf { it > 0 }
        }

        suspend fun getDetailByServerId(serverId: Long, memberId: String? = currentMemberId()): ExerciseRecord? {
            if (!isCurrentMember(memberId)) return null
            return exerciseRecordDao.getDetailByServerId(memberId!!, serverId)?.takeIf { it.syncState != SyncState.DELETE_PENDING }?.toModel()
        }

        suspend fun getDetailByLocalId(localId: String, memberId: String? = currentMemberId()): ExerciseRecord? {
            if (!isCurrentMember(memberId)) return null
            return exerciseRecordDao.getDetailByLocalId(memberId!!, localId)?.takeIf { it.syncState != SyncState.DELETE_PENDING }?.toModel()
        }

        suspend fun saveSyncedDetail(serverId: Long, record: ExerciseRecord, memberId: String? = currentMemberId(), previousLocalId: String? = null): String? {
            if (!isCurrentMember(memberId)) return null
            return database.withTransaction {
                if (!isCurrentMember(memberId)) return@withTransaction null
                val localId = exerciseRecordDao.findListItemLocalId(memberId!!, serverId)
                    ?: exerciseRecordDao.findDetailLocalId(memberId, serverId)
                    ?: previousLocalId
                    ?: newLocalId()
                if (serverId !in exerciseRecordDao.getAllPendingServerIds(memberId)) {
                    exerciseRecordDao.upsertDetail(record.toSyncedEntity(memberId, localId, serverId))
                }
                localId
            }
        }

        /** 쓰기 성공 후 목록·상세·집계를 같은 트랜잭션으로 맞춥니다. 날짜 이동도 이전 날짜에서 뺍니다. */
        suspend fun reconcileRecord(serverId: Long, record: ExerciseRecord, memberId: String?): String? {
            if (!isCurrentMember(memberId)) return null
            return database.withTransaction {
                if (!isCurrentMember(memberId)) return@withTransaction null
                val id = localId(memberId!!, serverId)
                if (serverId in exerciseRecordDao.getAllPendingServerIds(memberId)) return@withTransaction id
                val oldItem = exerciseRecordDao.getListItem(memberId, id)
                val oldDetail = exerciseRecordDao.getDetailByServerId(memberId, serverId)
                val oldDate = oldItem?.date ?: oldDetail?.startedAt?.toLocalDate()
                val date = record.startedAt.toLocalDate()
                exerciseRecordDao.upsertDetail(record.toSyncedEntity(memberId, id, serverId))
                exerciseRecordDao.upsertListItems(
                    listOf(
                        ExerciseListItemEntity(
                            memberId, id, serverId, SyncState.SYNCED, date, oldItem?.sortOrder ?: 0,
                            record.title, record.personalType, record.startedAt, record.endedAt, record.pictures?.map { it.url },
                        ),
                    ),
                )
                if (oldDate != date) {
                    oldDate?.let { adjustCount(memberId, it, -1) }
                    adjustCount(memberId, date, 1)
                }
                id
            }
        }

        suspend fun reconcileDeletedRecord(serverId: Long, memberId: String?) {
            if (!isCurrentMember(memberId)) return
            database.withTransaction {
                if (!isCurrentMember(memberId)) return@withTransaction
                if (serverId in exerciseRecordDao.getAllPendingServerIds(memberId!!)) return@withTransaction
                val id = exerciseRecordDao.findListItemLocalId(memberId, serverId) ?: exerciseRecordDao.findDetailLocalId(memberId, serverId) ?: return@withTransaction
                val date = exerciseRecordDao.getListItem(memberId, id)?.date
                    ?: exerciseRecordDao.getDetailByLocalId(memberId, id)?.startedAt?.toLocalDate()
                exerciseRecordDao.deleteListItem(memberId, id)
                exerciseRecordDao.deleteDetail(memberId, id)
                date?.let { adjustCount(memberId, it, -1) }
            }
        }

        /** 업로드 응답에는 URL이 없습니다. 후속 상세 조회가 실패하면 오래된 이미지를 내보내지 않습니다. */
        suspend fun invalidateImages(serverId: Long, memberId: String?) {
            if (!isCurrentMember(memberId)) return
            database.withTransaction {
                if (!isCurrentMember(memberId)) return@withTransaction
                if (serverId in exerciseRecordDao.getAllPendingServerIds(memberId!!)) return@withTransaction
                val id = exerciseRecordDao.findListItemLocalId(memberId, serverId) ?: exerciseRecordDao.findDetailLocalId(memberId, serverId) ?: return@withTransaction
                exerciseRecordDao.deleteDetail(memberId, id)
                exerciseRecordDao.getListItem(memberId, id)?.let { exerciseRecordDao.upsertListItems(listOf(it.copy(thumbnailUrls = null))) }
            }
        }

        private suspend fun localId(memberId: String, serverId: Long): String =
            exerciseRecordDao.findListItemLocalId(memberId, serverId)
                ?: exerciseRecordDao.findDetailLocalId(memberId, serverId)
                ?: newLocalId()

        private suspend fun adjustCount(memberId: String, date: LocalDate, delta: Int) {
            val count = exerciseCountDao.getRange(memberId, date, date).singleOrNull()?.count ?: 0
            exerciseCountDao.upsertAll(listOf(ExerciseCountEntity(memberId, date, (count + delta).coerceAtLeast(0))))
        }

        private fun newLocalId(): String = UUID.randomUUID().toString()
    }
