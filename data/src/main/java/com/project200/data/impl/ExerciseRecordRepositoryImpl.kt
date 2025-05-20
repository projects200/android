package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.mapper.toDomain
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.repository.ExerciseRecordRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class ExerciseRecordRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ExerciseRecordRepository {
    override suspend fun getExerciseDetail(recordId: Int): BaseResult<ExerciseRecord> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.getExerciseRecordDetail(recordId) },
            mapper = { dto: GetExerciseRecordData -> dto.toDomain() }
        )
    }
}