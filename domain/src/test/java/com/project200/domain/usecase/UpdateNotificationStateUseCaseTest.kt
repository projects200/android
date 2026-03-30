package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.NotificationState
import com.project200.domain.model.NotificationType
import com.project200.domain.repository.NotificationRepository
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
class UpdateNotificationStateUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: NotificationRepository

    private lateinit var useCase: UpdateNotificationStateUseCase

    private val sampleNotificationStates = listOf(
        NotificationState(type = NotificationType.CHAT_MESSAGE, enabled = true),
        NotificationState(type = NotificationType.WORKOUT_REMINDER, enabled = false)
    )

    @Before
    fun setUp() {
        useCase = UpdateNotificationStateUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 알림 상태 업데이트 성공`() = runTest {
        // Given
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.updateNotiState(sampleNotificationStates) } returns successResult

        // When
        val result = useCase(sampleNotificationStates)

        // Then
        coVerify(exactly = 1) { mockRepository.updateNotiState(sampleNotificationStates) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `알림 상태 업데이트 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("UPDATE_FAILED", "Failed to update notification state")
        coEvery { mockRepository.updateNotiState(sampleNotificationStates) } returns errorResult

        // When
        val result = useCase(sampleNotificationStates)

        // Then
        assertThat(result).isEqualTo(errorResult)
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("UPDATE_FAILED")
    }

    @Test
    fun `단일 알림 상태 업데이트`() = runTest {
        // Given
        val singleState = listOf(
            NotificationState(type = NotificationType.CHAT_MESSAGE, enabled = false)
        )
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.updateNotiState(singleState) } returns successResult

        // When
        val result = useCase(singleState)

        // Then
        coVerify(exactly = 1) { mockRepository.updateNotiState(singleState) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }

    @Test
    fun `모든 알림 활성화`() = runTest {
        // Given
        val allEnabled = listOf(
            NotificationState(type = NotificationType.CHAT_MESSAGE, enabled = true),
            NotificationState(type = NotificationType.WORKOUT_REMINDER, enabled = true)
        )
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.updateNotiState(allEnabled) } returns successResult

        // When
        val result = useCase(allEnabled)

        // Then
        coVerify(exactly = 1) { mockRepository.updateNotiState(allEnabled) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }

    @Test
    fun `모든 알림 비활성화`() = runTest {
        // Given
        val allDisabled = listOf(
            NotificationState(type = NotificationType.CHAT_MESSAGE, enabled = false),
            NotificationState(type = NotificationType.WORKOUT_REMINDER, enabled = false)
        )
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.updateNotiState(allDisabled) } returns successResult

        // When
        val result = useCase(allDisabled)

        // Then
        coVerify(exactly = 1) { mockRepository.updateNotiState(allDisabled) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }
}
