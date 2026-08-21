package com.project200.data.datasource

import androidx.room.withTransaction
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.dao.ExerciseCountDao
import com.project200.data.local.dao.ExerciseRecordDao
import com.project200.data.mapper.toEntity
import com.project200.data.mapper.toModel
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

/**
 * 운동 기록 캐시를 읽고 씁니다.
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
        fun observeCountsByRange(
            startDate: LocalDate,
            endDate: LocalDate,
        ): Flow<List<ExerciseCount>> {
            val memberId = currentMemberId() ?: return flowOf(emptyList())
            return exerciseCountDao.observeRange(memberId, startDate, endDate)
                .map { entities -> entities.map { it.toModel() } }
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

        fun observeListByDate(date: LocalDate): Flow<List<ExerciseListItem>> {
            val memberId = currentMemberId() ?: return flowOf(emptyList())
            return exerciseRecordDao.observeListByDate(memberId, date)
                .map { entities -> entities.map { it.toModel() } }
        }

        /** 서버에서 지워진 기록이 남지 않도록 그날 목록을 통째로 갈아끼웁니다 */
        suspend fun replaceListByDate(
            date: LocalDate,
            items: List<ExerciseListItem>,
        ) {
            val memberId = currentMemberId() ?: return
            database.withTransaction {
                exerciseRecordDao.deleteListByDate(memberId, date)
                exerciseRecordDao.upsertListItems(
                    items.mapIndexed { index, item -> item.toEntity(memberId, date, index) },
                )
            }
        }

        fun observeDetail(recordId: Long): Flow<ExerciseRecord?> {
            val memberId = currentMemberId() ?: return flowOf(null)
            return exerciseRecordDao.observeDetail(memberId, recordId)
                .map { entity -> entity?.toModel() }
        }

        suspend fun saveDetail(
            recordId: Long,
            record: ExerciseRecord,
        ) {
            val memberId = currentMemberId() ?: return
            exerciseRecordDao.upsertDetail(record.toEntity(memberId, recordId))
        }

        /** 기록이 삭제되면 목록과 상세를 함께 지웁니다 */
        suspend fun deleteRecord(recordId: Long) {
            val memberId = currentMemberId() ?: return
            database.withTransaction {
                exerciseRecordDao.deleteListItem(memberId, recordId)
                exerciseRecordDao.deleteDetail(memberId, recordId)
            }
        }

        private fun currentMemberId(): String? {
            return preferenceManager.getMemberId().also {
                if (it == null) Timber.w("회원ID가 없어 운동 기록 캐시를 건너뜁니다")
            }
        }
    }
