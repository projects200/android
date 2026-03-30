package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.BlockedMember
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
class GetBlockedMembersUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MemberRepository

    private lateinit var useCase: GetBlockedMembersUseCase

    private val sampleBlockedMembers = listOf(
        BlockedMember(
            memberBlockId = 1L,
            memberId = "member1",
            nickname = "차단된사용자1",
            profileImageUrl = "https://example.com/profile1.jpg",
            thumbnailImageUrl = "https://example.com/thumb1.jpg"
        ),
        BlockedMember(
            memberBlockId = 2L,
            memberId = "member2",
            nickname = "차단된사용자2",
            profileImageUrl = null,
            thumbnailImageUrl = null
        )
    )

    @Before
    fun setUp() {
        useCase = GetBlockedMembersUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 차단된 멤버 목록 성공적으로 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(sampleBlockedMembers)
        coEvery { mockRepository.getBlockedMembers() } returns successResult

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.getBlockedMembers() }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data).hasSize(2)
    }

    @Test
    fun `빈 차단 목록 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(emptyList<BlockedMember>())
        coEvery { mockRepository.getBlockedMembers() } returns successResult

        // When
        val result = useCase()

        // Then
        assertThat((result as BaseResult.Success).data).isEmpty()
    }

    @Test
    fun `차단 목록 조회 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR", "Failed to fetch blocked members")
        coEvery { mockRepository.getBlockedMembers() } returns errorResult

        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `프로필 이미지가 없는 차단 멤버 포함`() = runTest {
        // Given
        val blockedWithoutImage = listOf(
            BlockedMember(
                memberBlockId = 1L,
                memberId = "member1",
                nickname = "차단된사용자",
                profileImageUrl = null,
                thumbnailImageUrl = null
            )
        )
        coEvery { mockRepository.getBlockedMembers() } returns BaseResult.Success(blockedWithoutImage)

        // When
        val result = useCase()

        // Then
        val data = (result as BaseResult.Success).data
        assertThat(data[0].profileImageUrl).isNull()
        assertThat(data[0].thumbnailImageUrl).isNull()
    }

    @Test
    fun `많은 수의 차단 멤버 반환`() = runTest {
        // Given
        val manyBlockedMembers = (1..50).map {
            BlockedMember(
                memberBlockId = it.toLong(),
                memberId = "member$it",
                nickname = "차단된사용자$it",
                profileImageUrl = null,
                thumbnailImageUrl = null
            )
        }
        coEvery { mockRepository.getBlockedMembers() } returns BaseResult.Success(manyBlockedMembers)

        // When
        val result = useCase()

        // Then
        assertThat((result as BaseResult.Success).data).hasSize(50)
    }
}
