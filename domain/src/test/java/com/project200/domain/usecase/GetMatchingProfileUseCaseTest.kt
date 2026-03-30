package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.MatchingMemberProfile
import com.project200.domain.model.PreferredExercise
import com.project200.domain.repository.MatchingRepository
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
class GetMatchingProfileUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MatchingRepository

    private lateinit var useCase: GetMatchingProfileUseCase

    private val sampleMemberId = "member123"

    private val sampleProfile = MatchingMemberProfile(
        profileThumbnailUrl = "https://example.com/thumb.jpg",
        profileImageUrl = "https://example.com/profile.jpg",
        nickname = "운동왕",
        gender = "M",
        birthDate = "1990-01-01",
        bio = "매일 운동하는 직장인입니다",
        yearlyExerciseDays = 200,
        exerciseCountInLast30Days = 25,
        exerciseScore = 85,
        preferredExercises = emptyList()
    )

    @Before
    fun setUp() {
        useCase = GetMatchingProfileUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 매칭 프로필 성공적으로 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(sampleProfile)
        coEvery { mockRepository.getMatchingProfile(sampleMemberId) } returns successResult

        // When
        val result = useCase(sampleMemberId)

        // Then
        coVerify(exactly = 1) { mockRepository.getMatchingProfile(sampleMemberId) }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data.nickname).isEqualTo("운동왕")
    }

    @Test
    fun `프로필 이미지 없는 멤버 프로필 반환`() = runTest {
        // Given
        val profileWithoutImage = sampleProfile.copy(
            profileThumbnailUrl = null,
            profileImageUrl = null
        )
        coEvery { mockRepository.getMatchingProfile(sampleMemberId) } returns BaseResult.Success(profileWithoutImage)

        // When
        val result = useCase(sampleMemberId)

        // Then
        val data = (result as BaseResult.Success).data
        assertThat(data.profileThumbnailUrl).isNull()
        assertThat(data.profileImageUrl).isNull()
    }

    @Test
    fun `바이오 없는 멤버 프로필 반환`() = runTest {
        // Given
        val profileWithoutBio = sampleProfile.copy(bio = null)
        coEvery { mockRepository.getMatchingProfile(sampleMemberId) } returns BaseResult.Success(profileWithoutBio)

        // When
        val result = useCase(sampleMemberId)

        // Then
        assertThat((result as BaseResult.Success).data.bio).isNull()
    }

    @Test
    fun `존재하지 않는 멤버 조회 시 에러 반환`() = runTest {
        // Given
        val errorResult = BaseResult.Error("NOT_FOUND", "Member not found")
        coEvery { mockRepository.getMatchingProfile("nonexistent") } returns errorResult

        // When
        val result = useCase("nonexistent")

        // Then
        assertThat(result).isEqualTo(errorResult)
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("NOT_FOUND")
    }

    @Test
    fun `운동 통계가 0인 멤버 프로필 반환`() = runTest {
        // Given
        val newMemberProfile = sampleProfile.copy(
            yearlyExerciseDays = 0,
            exerciseCountInLast30Days = 0,
            exerciseScore = 0
        )
        coEvery { mockRepository.getMatchingProfile(sampleMemberId) } returns BaseResult.Success(newMemberProfile)

        // When
        val result = useCase(sampleMemberId)

        // Then
        val data = (result as BaseResult.Success).data
        assertThat(data.yearlyExerciseDays).isEqualTo(0)
        assertThat(data.exerciseCountInLast30Days).isEqualTo(0)
        assertThat(data.exerciseScore).isEqualTo(0)
    }
}
