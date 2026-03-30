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
import java.time.LocalDate

@ExperimentalCoroutinesApi
class SignUpUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: AuthRepository

    private lateinit var useCase: SignUpUseCase

    private val testGender = "MALE"
    private val testNickname = "테스트유저"
    private val testBirth = LocalDate.of(1990, 1, 1)

    @Before
    fun setUp() {
        useCase = SignUpUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 repository signUp 호출 및 성공 결과 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.signUp(testGender, testNickname, testBirth) } returns successResult

        // When
        val result = useCase(testGender, testNickname, testBirth)

        // Then
        coVerify(exactly = 1) { mockRepository.signUp(testGender, testNickname, testBirth) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `invoke 호출 시 repository signUp 호출 및 실패 결과 반환`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR", "SignUp failed")
        coEvery { mockRepository.signUp(testGender, testNickname, testBirth) } returns errorResult

        // When
        val result = useCase(testGender, testNickname, testBirth)

        // Then
        coVerify(exactly = 1) { mockRepository.signUp(testGender, testNickname, testBirth) }
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `다양한 성별 값으로 signUp 호출`() = runTest {
        // Given
        val femaleGender = "FEMALE"
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.signUp(femaleGender, testNickname, testBirth) } returns successResult

        // When
        val result = useCase(femaleGender, testNickname, testBirth)

        // Then
        coVerify(exactly = 1) { mockRepository.signUp(femaleGender, testNickname, testBirth) }
        assertThat(result).isEqualTo(successResult)
    }
}
