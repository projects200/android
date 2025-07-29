package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.mapper.toDomain
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExpectedScoreInfo
import com.project200.domain.model.PolicyGroup
import com.project200.domain.model.ValidWindow
import com.project200.domain.repository.PolicyRepository
import kotlinx.coroutines.CoroutineDispatcher
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

class PolicyRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : PolicyRepository {

    override suspend fun getPolicyGroup(groupName: String): BaseResult<PolicyGroup> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.getPolicyGroup(groupName) },
            mapper = { it?.toDomain() ?: throw NoSuchElementException("정책 데이터가 없습니다.") }
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