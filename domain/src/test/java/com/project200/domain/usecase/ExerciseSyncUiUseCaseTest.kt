package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExercisePointNotice
import com.project200.domain.model.ExerciseSyncStatus
import com.project200.domain.model.canShowExerciseBodyRetry
import com.project200.domain.repository.ExerciseRecordRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ExerciseSyncUiUseCaseTest {
    @Test
    fun `검증된 인터넷 인증 본문 실패 작업 없음의 모든 조건을 만족해야 재시도를 표시한다`() {
        for (network in listOf(false, true)) {
            for (account in listOf(false, true)) {
                for (failure in listOf(false, true)) {
                    for (busy in listOf(false, true)) {
                        assertThat(canShowExerciseBodyRetry(network, account, failure, busy))
                            .isEqualTo(network && account && failure && !busy)
                    }
                }
            }
        }
    }

    @Test
    fun `화면 상태 관찰과 재시도 폐기 점수 소비는 같은 domain 저장소 계약을 사용한다`() =
        runTest {
            val repository = mockk<ExerciseRecordRepository>()
            val status = ExerciseSyncStatus("record", canShowBodyRetry = true)
            val notice = ExercisePointNotice("record", 3)
            every { repository.observeSyncStatus() } returns flowOf(listOf(status))
            coEvery { repository.retryBody("record") } returns true
            coEvery { repository.discardRemoteDeleted("record", "approval") } returns BaseResult.Success(Unit)
            coEvery { repository.consumePointNotice(setOf("record")) } returns notice
            val useCase = ExerciseSyncUiUseCase(repository)
            assertThat(useCase.observe().first()).containsExactly(status)
            assertThat(useCase.retry("record")).isTrue()
            assertThat(useCase.discardRemoteDeleted("record", "approval")).isEqualTo(BaseResult.Success(Unit))
            assertThat(useCase.consumePointNotice(setOf("record"))).isEqualTo(notice)
            coVerify(exactly = 1) { repository.discardRemoteDeleted("record", "approval") }
        }
}
