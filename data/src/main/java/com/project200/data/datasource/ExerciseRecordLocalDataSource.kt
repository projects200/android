package com.project200.data.datasource

import androidx.room.withTransaction
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.dao.ExerciseCountDao
import com.project200.data.local.dao.ExerciseRecordDao
import com.project200.data.mapper.toEntity
import com.project200.data.mapper.toModel
import com.project200.data.mapper.toSyncedEntity
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
import timber.log.Timber
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * 운동 기록 캐시를 읽고 씁니다.
 *
 * 읽기는 한 번에 끝납니다. 화면이 지속 구독을 하지 않아서 한 번 그린 값이
 * 저절로 바뀌지 않습니다
 *
 * 계정 조건을 여기서 한 번에 겁니다. 호출부가 memberId를 넘기지 않게 해서
 * 조건을 빠뜨린 쿼리가 나올 자리를 없앱니다
 *
 * 로그아웃 상태에서는 읽기가 빈 값을, 쓰기가 아무 일도 하지 않습니다
 */
class ExerciseRecordLocalDataSource
    @Inject
    constructor(
        private val database: UndabangDatabase,
        private val exerciseCountDao: ExerciseCountDao,
        private val exerciseRecordDao: ExerciseRecordDao,
        private val preferenceManager: PreferenceManager,
    ) {
        suspend fun getCountsByRange(
            startDate: LocalDate,
            endDate: LocalDate,
        ): List<ExerciseCount> {
            val memberId = currentMemberId() ?: return emptyList()
            return exerciseCountDao.getRange(memberId, startDate, endDate).map { it.toModel() }
        }

        /** 서버 응답에는 운동한 날만 들어 있어서 구간을 비우고 다시 채웁니다 */
        suspend fun replaceCountsByRange(
            startDate: LocalDate,
            endDate: LocalDate,
            counts: List<ExerciseCount>,
        ) {
            val memberId = currentMemberId() ?: return
            database.withTransaction {
                exerciseCountDao.deleteRange(memberId, startDate, endDate)
                exerciseCountDao.upsertAll(counts.map { it.toEntity(memberId) })
            }
        }

        suspend fun getListByDate(date: LocalDate): List<ExerciseListItem> {
            val memberId = currentMemberId() ?: return emptyList()
            return exerciseRecordDao.getListByDate(memberId, date).map { it.toModel() }
        }

        /**
         * 서버에서 지워진 기록이 남지 않도록 그날 목록을 갈아끼웁니다.
         *
         * 아직 못 올린 로컬 변경이 사라지면 안 되므로 전송 대기 행은 두고 갑니다.
         * 이미 캐시에 있던 기록은 localId를 이어 써서, 목록을 새로 받아도
         * 그 기록을 가리키던 화면과 이미지가 같은 행을 계속 가리킵니다
         */
        suspend fun replaceSyncedListByDate(
            date: LocalDate,
            items: List<ExerciseListItem>,
        ) {
            val memberId = currentMemberId() ?: return
            database.withTransaction {
                val knownLocalIds =
                    exerciseRecordDao.getServerIdToLocalId(memberId, date)
                        .associate { it.serverId to it.localId }

                exerciseRecordDao.deleteSyncedListByDate(memberId, date)
                exerciseRecordDao.upsertListItems(
                    items.mapIndexed { index, item ->
                        val localId = knownLocalIds[item.recordId] ?: newLocalId()
                        item.toSyncedEntity(memberId, localId, date, index)
                    },
                )
            }
        }

        suspend fun getDetailByServerId(serverId: Long): ExerciseRecord? {
            val memberId = currentMemberId() ?: return null
            return exerciseRecordDao.getDetailByServerId(memberId, serverId)?.toModel()
        }

        suspend fun getDetailByLocalId(localId: String): ExerciseRecord? {
            val memberId = currentMemberId() ?: return null
            return exerciseRecordDao.getDetailByLocalId(memberId, localId)?.toModel()
        }

        /** 상세를 받아온 기록이 목록에도 있으면 그 localId를 씁니다 */
        suspend fun saveSyncedDetail(
            serverId: Long,
            record: ExerciseRecord,
        ) {
            val memberId = currentMemberId() ?: return
            database.withTransaction {
                val localId =
                    exerciseRecordDao.findDetailLocalId(memberId, serverId)
                        ?: exerciseRecordDao.findListItemLocalId(memberId, serverId)
                        ?: newLocalId()
                exerciseRecordDao.upsertDetail(record.toSyncedEntity(memberId, localId, serverId))
            }
        }

        /** 기록이 삭제되면 목록과 상세를 함께 지웁니다 */
        suspend fun deleteRecord(localId: String) {
            val memberId = currentMemberId() ?: return
            database.withTransaction {
                exerciseRecordDao.deleteListItem(memberId, localId)
                exerciseRecordDao.deleteDetail(memberId, localId)
            }
        }

        private fun newLocalId(): String = UUID.randomUUID().toString()

        private fun currentMemberId(): String? {
            return preferenceManager.getMemberId().also {
                if (it == null) Timber.w("회원ID가 없어 운동 기록 캐시를 건너뜁니다")
            }
        }
    }
