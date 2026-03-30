package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.PreferredExercise
import com.project200.domain.model.UserProfile
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
class GetUserProfileUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MemberRepository

    private lateinit var useCase: GetUserProfileUseCase

    private val sampleUserProfile = UserProfile(
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
        useCase = GetUserProfileUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 유저 프로필 성공적으로 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(sampleUserProfile)
        coEvery { mockRepository.getUserProfile() } returns successResult

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.getUserProfile() }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data.nickname).isEqualTo("운동왕")
    }

    @Test
    fun `프로필 이미지가 없는 경우`() = runTest {
        // Given
        val profileWithoutImage = sampleUserProfile.copy(
            profileThumbnailUrl = null,
            profileImageUrl = null
        )
        coEvery { mockRepository.getUserProfile() } returns BaseResult.Success(profileWithoutImage)

        // When
        val result = useCase()

        // Then
        val data = (result as BaseResult.Success).data
        assertThat(data.profileThumbnailUrl).isNull()
        assertThat(data.profileImageUrl).isNull()
    }

    @Test
    fun `바이오가 없는 경우`() = runTest {
        // Given
        val profileWithoutBio = sampleUserProfile.copy(bio = null)
        coEvery { mockRepository.getUserProfile() } returns BaseResult.Success(profileWithoutBio)

        // When
        val result = useCase()

        // Then
        assertThat((result as BaseResult.Success).data.bio).isNull()
    }

    @Test
    fun `유저 프로필 조회 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("UNAUTHORIZED", "User not authenticated")
        coEvery { mockRepository.getUserProfile() } returns errorResult

        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(errorResult)
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("UNAUTHORIZED")
    }

    @Test
    fun `운동 기록이 없는 신규 유저 프로필`() = runTest {
        // Given
        val newUserProfile = sampleUserProfile.copy(
            yearlyExerciseDays = 0,
            exerciseCountInLast30Days = 0,
            exerciseScore = 0
        )
        coEvery { mockRepository.getUserProfile() } returns BaseResult.Success(newUserProfile)

        // When
        val result = useCase()

        // Then
        val data = (result as BaseResult.Success).data
        assertThat(data.yearlyExerciseDays).isEqualTo(0)
        assertThat(data.exerciseCountInLast30Days).isEqualTo(0)
        assertThat(data.exerciseScore).isEqualTo(0)
    }
}
