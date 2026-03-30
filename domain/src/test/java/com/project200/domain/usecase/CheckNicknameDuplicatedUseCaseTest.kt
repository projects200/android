package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.repository.AuthRepository
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
class CheckNicknameDuplicatedUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: AuthRepository

    private lateinit var useCase: CheckNicknameDuplicatedUseCase

    @Before
    fun setUp() {
        useCase = CheckNicknameDuplicatedUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 닉네임이 중복되면 true 반환`() = runTest {
        // Given
        val nickname = "중복닉네임"
        val successResult = BaseResult.Success(true)
        coEvery { mockRepository.checkNicknameDuplicated(nickname) } returns successResult

        // When
        val result = useCase(nickname)

        // Then
        coVerify(exactly = 1) { mockRepository.checkNicknameDuplicated(nickname) }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data).isTrue()
    }

    @Test
    fun `invoke 호출 시 닉네임이 중복되지 않으면 false 반환`() = runTest {
        // Given
        val nickname = "사용가능닉네임"
        val successResult = BaseResult.Success(false)
        coEvery { mockRepository.checkNicknameDuplicated(nickname) } returns successResult

        // When
        val result = useCase(nickname)

        // Then
        coVerify(exactly = 1) { mockRepository.checkNicknameDuplicated(nickname) }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data).isFalse()
    }

    @Test
    fun `invoke 호출 시 에러 발생하면 Error 반환`() = runTest {
        // Given
        val nickname = "테스트"
        val errorResult = BaseResult.Error("ERR", "Network error")
        coEvery { mockRepository.checkNicknameDuplicated(nickname) } returns errorResult

        // When
        val result = useCase(nickname)

        // Then
        coVerify(exactly = 1) { mockRepository.checkNicknameDuplicated(nickname) }
        assertThat(result).isEqualTo(errorResult)
    }
}
