package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.repository.FcmRepository
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
class SendFcmTokenUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: FcmRepository

    private lateinit var useCase: SendFcmTokenUseCase

    @Before
    fun setUp() {
        useCase = SendFcmTokenUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 FCM 토큰 전송 성공`() = runTest {
        // Given
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.sendFcmToken() } returns successResult

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.sendFcmToken() }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `FCM 토큰 전송 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("SEND_FAILED", "Failed to send FCM token")
        coEvery { mockRepository.sendFcmToken() } returns errorResult

        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(errorResult)
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("SEND_FAILED")
    }

    @Test
    fun `네트워크 오류로 전송 실패`() = runTest {
        // Given
        val networkError = BaseResult.Error("NETWORK_ERROR", "Network connection failed")
        coEvery { mockRepository.sendFcmToken() } returns networkError

        // When
        val result = useCase()

        // Then
        assertThat((result as BaseResult.Error).message).isEqualTo("Network connection failed")
    }

    @Test
    fun `인증 오류로 전송 실패`() = runTest {
        // Given
        val authError = BaseResult.Error("UNAUTHORIZED", "User not authenticated")
        coEvery { mockRepository.sendFcmToken() } returns authError

        // When
        val result = useCase()

        // Then
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("UNAUTHORIZED")
    }
}
