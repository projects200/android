package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord

interface ExerciseRecordRepository {
    suspend fun getExerciseDetail(recordId: Long): BaseResult<ExerciseRecord>
    suspend fun createExerciseRecord(record: ExerciseRecord): BaseResult<Long>
    suspend fun uploadExerciseRecordImages(recordId:Long, images: List<String>): BaseResult<Long>
}