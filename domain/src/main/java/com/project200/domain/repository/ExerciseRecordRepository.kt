package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
import java.time.LocalDate

interface ExerciseRecordRepository {
    suspend fun getExerciseCountByRange(startDate: LocalDate, endDate: LocalDate): BaseResult<List<ExerciseCount>>
    suspend fun getExerciseDetail(recordId: Long): BaseResult<ExerciseRecord>
    suspend fun getExerciseRecordList(date: LocalDate): BaseResult<List<ExerciseListItem>>
    suspend fun createExerciseRecord(record: ExerciseRecord): BaseResult<Long>
    suspend fun updateExerciseRecord(record: ExerciseRecord, recordId: Long): BaseResult<Long>
    suspend fun uploadExerciseRecordImages(recordId:Long, images: List<String>): BaseResult<Long>
    suspend fun deleteExerciseRecordImages(recordId: Long, imageIds: List<Long>): BaseResult<Unit>
    suspend fun deleteExerciseRecord(recordId: Long): BaseResult<Unit>
}