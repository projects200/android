package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.GetScoreDTO
import com.project200.data.mapper.toModel
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.PolicyType
import com.project200.domain.model.ScorePolicy
import com.project200.domain.repository.PolicyRepository
import kotlinx.coroutines.CoroutineDispatcher
import java.util.NoSuchElementException
import javax.inject.Inject

class PolicyRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : PolicyRepository {

    override suspend fun getScorePolicy(): BaseResult<List<ScorePolicy>> {
        /*return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.get() },
            mapper = { dto: GetScoreDTO? ->
                dto?.toModel() ?: throw NoSuchElementException("점수 조회 데이터가 없습니다.")
            }
        )*/

        return BaseResult.Success(
            listOf(
                ScorePolicy(PolicyType.EXERCISE_SCORE_MAX_POINTS.key, 100, "POINTS"),
                ScorePolicy(PolicyType.EXERCISE_SCORE_MIN_POINTS.key, 0, "POINTS"),
                ScorePolicy(PolicyType.SIGNUP_INITIAL_POINTS.key, 35, "POINTS"),
                ScorePolicy(PolicyType.POINTS_PER_EXERCISE.key, 3, "POINTS"),
                ScorePolicy(PolicyType.EXERCISE_RECORD_VALIDITY_PERIOD.key, 2, "DAYS"),
                ScorePolicy(PolicyType.PENALTY_INACTIVITY_THRESHOLD_DAYS.key, 7, "DAYS"),
                ScorePolicy(PolicyType.PENALTY_SCORE_DECREMENT_POINTS.key, 1, "POINTS")
            )
        )
    }
}