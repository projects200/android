package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Policy
import com.project200.domain.model.PolicyGroup
import com.project200.domain.repository.PolicyRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class GetScorePolicyUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: PolicyRepository

    private lateinit var useCase: GetScorePolicyUseCase

    private val samplePolicies = listOf(
        Policy(
            policyKey = "daily_max",
            policyValue = "10",
            policyUnit = "points",
            policyDescription = "하루 최대 획득 점수"
        ),
        Policy(
            policyKey = "per_exercise",
            policyValue = "2",
            policyUnit = "points",
            policyDescription = "운동 1회당 점수"
        )
    )

    private val samplePolicyGroup = PolicyGroup(
        groupName = "exercise-score",
        size = 2,
        policies = samplePolicies
    )

    @Before
    fun setUp() {
        useCase = GetScorePolicyUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 점수 정책 성공적으로 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(samplePolicyGroup)
        coEvery { mockRepository.getPolicyGroup("exercise-score") } returns successResult

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.getPolicyGroup("exercise-score") }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data.groupName).isEqualTo("exercise-score")
    }

    @Test
    fun `정책 그룹의 정책 목록 확인`() = runTest {
        // Given
        coEvery { mockRepository.getPolicyGroup("exercise-score") } returns BaseResult.Success(samplePolicyGroup)

        // When
        val result = useCase()

        // Then
        val data = (result as BaseResult.Success).data
        assertThat(data.policies).hasSize(2)
        assertThat(data.policies[0].policyKey).isEqualTo("daily_max")
    }

    @Test
    fun `점수 정책 조회 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("NOT_FOUND", "Policy group not found")
        coEvery { mockRepository.getPolicyGroup("exercise-score") } returns errorResult

        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(errorResult)
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("NOT_FOUND")
    }

    @Test
    fun `빈 정책 목록 반환`() = runTest {
        // Given
        val emptyPolicyGroup = PolicyGroup(
            groupName = "exercise-score",
            size = 0,
            policies = emptyList()
        )
        coEvery { mockRepository.getPolicyGroup("exercise-score") } returns BaseResult.Success(emptyPolicyGroup)

        // When
        val result = useCase()

        // Then
        assertThat((result as BaseResult.Success).data.policies).isEmpty()
    }
}
