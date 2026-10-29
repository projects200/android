package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseWriteInput
import com.project200.domain.model.LocalExerciseSaveResult
import java.time.LocalDate

interface ExerciseRecordRepository {
    /** refresh=false는 캐시 우선이며 온라인 미적중은 갱신합니다. 복귀 시 localOnly=true로 서버 조회를 막습니다. */
    suspend fun getExerciseCountByRange(startDate: LocalDate, endDate: LocalDate, refresh: Boolean = false, localOnly: Boolean = false): BaseResult<List<ExerciseCount>>
    suspend fun getExerciseDetail(recordId: String, refresh: Boolean = false, localOnly: Boolean = false): BaseResult<ExerciseRecord>
    suspend fun getExerciseRecordList(date: LocalDate, refresh: Boolean = false, localOnly: Boolean = false): BaseResult<List<ExerciseListItem>>
    suspend fun createExerciseRecord(input: ExerciseWriteInput): BaseResult<LocalExerciseSaveResult>
    suspend fun updateExerciseRecord(input: ExerciseWriteInput, recordId: String): BaseResult<LocalExerciseSaveResult>
    suspend fun uploadExerciseRecordImages(recordId: String, images: List<String>): BaseResult<String>
    suspend fun deleteExerciseRecordImages(recordId: String, imageIds: List<Long>): BaseResult<Unit>
    suspend fun deleteExerciseRecord(recordId: String): BaseResult<Unit>
}
