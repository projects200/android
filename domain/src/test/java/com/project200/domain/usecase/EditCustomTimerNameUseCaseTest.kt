package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.repository.TimerRepository
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
class EditCustomTimerNameUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: TimerRepository

    private lateinit var useCase: EditCustomTimerNameUseCase

    @Before
    fun setUp() {
        useCase = EditCustomTimerNameUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 타이머 이름 수정 성공`() = runTest {
        // Given
        val customTimerId = 1L
        val newTitle = "새로운 타이머 이름"
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.editCustomTimerTitle(customTimerId, newTitle) } returns successResult

        // When
        val result = useCase(customTimerId, newTitle)

        // Then
        coVerify(exactly = 1) { mockRepository.editCustomTimerTitle(customTimerId, newTitle) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `invoke 호출 시 타이머 이름 수정 실패`() = runTest {
        // Given
        val customTimerId = 1L
        val newTitle = "새로운 타이머 이름"
        val errorResult = BaseResult.Error("ERR", "Edit failed")
        coEvery { mockRepository.editCustomTimerTitle(customTimerId, newTitle) } returns errorResult

        // When
        val result = useCase(customTimerId, newTitle)

        // Then
        coVerify(exactly = 1) { mockRepository.editCustomTimerTitle(customTimerId, newTitle) }
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `빈 문자열로 타이머 이름 수정`() = runTest {
        // Given
        val customTimerId = 1L
        val emptyTitle = ""
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.editCustomTimerTitle(customTimerId, emptyTitle) } returns successResult

        // When
        val result = useCase(customTimerId, emptyTitle)

        // Then
        coVerify(exactly = 1) { mockRepository.editCustomTimerTitle(customTimerId, emptyTitle) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }
}
