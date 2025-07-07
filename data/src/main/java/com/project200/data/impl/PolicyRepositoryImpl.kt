package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.GetScoreDTO
import com.project200.data.mapper.toModel
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExpectedScoreInfo
import com.project200.domain.model.PolicyType
import com.project200.domain.model.ScorePolicy
import com.project200.domain.model.ValidWindow
import com.project200.domain.repository.PolicyRepository
import kotlinx.coroutines.CoroutineDispatcher
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.NoSuchElementException
import javax.inject.Inject

class PolicyRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : PolicyRepository {

    override suspend fun getScorePolicy(): BaseResult<List<ScorePolicy>> {
        // TODO: 실제 api와 연결

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

    override suspend fun getExpectedScoreInfo(): BaseResult<ExpectedScoreInfo> {
        // TODO 실제 예상 점수 획득 정보 조회 api

        // 더미 데이터 생성
        val now = LocalDateTime.now()
        val today = LocalDate.now()

        val dummyExpectedScoreInfo = ExpectedScoreInfo(
            pointsPerExercise = 3,
            currentUserScore = 99,
            maxScore = 100,
            validWindow = ValidWindow(
                startDateTime = now.minusDays(1).withHour(0).withMinute(0).withSecond(0)
                    .withNano(0), // 2일 전 00:00:00
                endDateTime = now
            ),
            earnableScoreDays = listOf(
                today.minusDays(1) // 어제 날짜 (이미 점수를 획득했다고 가정)
            )
        )

        return BaseResult.Success(dummyExpectedScoreInfo)
    }


}