package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.repository.MemberRepository
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
class UnblockMemberUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MemberRepository

    private lateinit var useCase: UnblockMemberUseCase

    private val sampleMemberId = "member123"

    @Before
    fun setUp() {
        useCase = UnblockMemberUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 멤버 차단 해제 성공`() = runTest {
        // Given
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.unblockMember(sampleMemberId) } returns successResult

        // When
        val result = useCase(sampleMemberId)

        // Then
        coVerify(exactly = 1) { mockRepository.unblockMember(sampleMemberId) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `멤버 차단 해제 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("UNBLOCK_FAILED", "Failed to unblock member")
        coEvery { mockRepository.unblockMember(sampleMemberId) } returns errorResult

        // When
        val result = useCase(sampleMemberId)

        // Then
        assertThat(result).isEqualTo(errorResult)
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("UNBLOCK_FAILED")
    }

    @Test
    fun `차단되지 않은 멤버 해제 시 에러`() = runTest {
        // Given
        val errorResult = BaseResult.Error("NOT_BLOCKED", "Member is not blocked")
        coEvery { mockRepository.unblockMember(sampleMemberId) } returns errorResult

        // When
        val result = useCase(sampleMemberId)

        // Then
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("NOT_BLOCKED")
    }

    @Test
    fun `존재하지 않는 멤버 차단 해제 시 에러`() = runTest {
        // Given
        val nonExistentMemberId = "nonexistent"
        val errorResult = BaseResult.Error("NOT_FOUND", "Member not found")
        coEvery { mockRepository.unblockMember(nonExistentMemberId) } returns errorResult

        // When
        val result = useCase(nonExistentMemberId)

        // Then
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("NOT_FOUND")
    }

    @Test
    fun `다른 멤버 차단 해제`() = runTest {
        // Given
        val anotherMemberId = "member456"
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.unblockMember(anotherMemberId) } returns successResult

        // When
        val result = useCase(anotherMemberId)

        // Then
        coVerify(exactly = 1) { mockRepository.unblockMember(anotherMemberId) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }
}
