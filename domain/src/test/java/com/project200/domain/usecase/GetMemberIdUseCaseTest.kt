package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
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
class GetMemberIdUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: AuthRepository

    private lateinit var useCase: GetMemberIdUseCase

    @Before
    fun setUp() {
        useCase = GetMemberIdUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 repository getMemberId 호출 및 memberId 반환`() = runTest {
        // Given
        val expectedMemberId = "member_12345"
        coEvery { mockRepository.getMemberId() } returns expectedMemberId

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.getMemberId() }
        assertThat(result).isEqualTo(expectedMemberId)
    }

    @Test
    fun `invoke 호출 시 빈 문자열 memberId 반환`() = runTest {
        // Given
        val expectedMemberId = ""
        coEvery { mockRepository.getMemberId() } returns expectedMemberId

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.getMemberId() }
        assertThat(result).isEmpty()
    }
}
