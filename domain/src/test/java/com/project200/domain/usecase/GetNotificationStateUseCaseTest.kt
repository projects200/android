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
class GetNotificationStateUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: NotificationRepository

    private lateinit var useCase: GetNotificationStateUseCase

    private val sampleNotificationStates = listOf(
        NotificationState(type = NotificationType.CHAT_MESSAGE, enabled = true),
        NotificationState(type = NotificationType.WORKOUT_REMINDER, enabled = true)
    )

    @Before
    fun setUp() {
        useCase = GetNotificationStateUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 알림 상태 성공적으로 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(sampleNotificationStates)
        coEvery { mockRepository.getNotiState() } returns successResult

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.getNotiState() }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data).hasSize(2)
    }

    @Test
    fun `빈 알림 상태 목록 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(emptyList<NotificationState>())
        coEvery { mockRepository.getNotiState() } returns successResult

        // When
        val result = useCase()

        // Then
        assertThat((result as BaseResult.Success).data).isEmpty()
    }

    @Test
    fun `알림 상태 조회 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR", "Failed to fetch notification state")
        coEvery { mockRepository.getNotiState() } returns errorResult

        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `채팅 알림만 활성화된 상태`() = runTest {
        // Given
        val chatOnlyEnabled = listOf(
            NotificationState(type = NotificationType.CHAT_MESSAGE, enabled = true),
            NotificationState(type = NotificationType.WORKOUT_REMINDER, enabled = false)
        )
        coEvery { mockRepository.getNotiState() } returns BaseResult.Success(chatOnlyEnabled)

        // When
        val result = useCase()

        // Then
        val data = (result as BaseResult.Success).data
        assertThat(data.first { it.type == NotificationType.CHAT_MESSAGE }.enabled).isTrue()
        assertThat(data.first { it.type == NotificationType.WORKOUT_REMINDER }.enabled).isFalse()
    }

    @Test
    fun `모든 알림 비활성화된 상태`() = runTest {
        // Given
        val allDisabled = listOf(
            NotificationState(type = NotificationType.CHAT_MESSAGE, enabled = false),
            NotificationState(type = NotificationType.WORKOUT_REMINDER, enabled = false)
        )
        coEvery { mockRepository.getNotiState() } returns BaseResult.Success(allDisabled)

        // When
        val result = useCase()

        // Then
        val data = (result as BaseResult.Success).data
        assertThat(data.all { !it.enabled }).isTrue()
    }
}
