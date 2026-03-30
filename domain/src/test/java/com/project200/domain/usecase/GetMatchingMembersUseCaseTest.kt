package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Location
import com.project200.domain.model.MapBounds
import com.project200.domain.model.MatchingMember
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
class GetMatchingMembersUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MatchingRepository

    private lateinit var useCase: GetMatchingMembersUseCase

    private val sampleMapBounds = MapBounds(
        topLeftLat = 37.6,
        topLeftLng = 126.9,
        bottomRightLat = 37.4,
        bottomRightLng = 127.1
    )

    private val sampleMatchingMembers = listOf(
        MatchingMember(
            memberId = "member1",
            profileThumbnailUrl = "https://example.com/thumb1.jpg",
            profileImageUrl = "https://example.com/profile1.jpg",
            nickname = "운동왕",
            gender = "M",
            birthDate = "1990-01-01",
            memberScore = 85,
            locations = listOf(
                Location(
                    placeId = 1L,
                    placeName = "강남 헬스장",
                    latitude = 37.5,
                    longitude = 127.0
                )
            ),
            preferredExercises = emptyList()
        ),
        MatchingMember(
            memberId = "member2",
            profileThumbnailUrl = null,
            profileImageUrl = null,
            nickname = "헬스초보",
            gender = "F",
            birthDate = "1995-05-15",
            memberScore = 60,
            locations = emptyList(),
            preferredExercises = emptyList()
        )
    )

    @Before
    fun setUp() {
        useCase = GetMatchingMembersUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 매칭 멤버 목록 성공적으로 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(sampleMatchingMembers)
        coEvery { mockRepository.getMembers(sampleMapBounds) } returns successResult

        // When
        val result = useCase(sampleMapBounds)

        // Then
        coVerify(exactly = 1) { mockRepository.getMembers(sampleMapBounds) }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data).hasSize(2)
    }

    @Test
    fun `빈 매칭 멤버 목록 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(emptyList<MatchingMember>())
        coEvery { mockRepository.getMembers(sampleMapBounds) } returns successResult

        // When
        val result = useCase(sampleMapBounds)

        // Then
        assertThat((result as BaseResult.Success).data).isEmpty()
    }

    @Test
    fun `매칭 멤버 조회 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR_MATCHING", "Failed to fetch matching members")
        coEvery { mockRepository.getMembers(sampleMapBounds) } returns errorResult

        // When
        val result = useCase(sampleMapBounds)

        // Then
        assertThat(result).isEqualTo(errorResult)
        assertThat((result as BaseResult.Error).message).isEqualTo("Failed to fetch matching members")
    }

    @Test
    fun `다른 지도 영역으로 조회 시 해당 영역의 멤버 반환`() = runTest {
        // Given
        val differentBounds = MapBounds(
            topLeftLat = 35.2,
            topLeftLng = 128.5,
            bottomRightLat = 35.0,
            bottomRightLng = 128.7
        )
        val differentAreaMembers = listOf(sampleMatchingMembers[0])
        coEvery { mockRepository.getMembers(differentBounds) } returns BaseResult.Success(differentAreaMembers)

        // When
        val result = useCase(differentBounds)

        // Then
        coVerify(exactly = 1) { mockRepository.getMembers(differentBounds) }
        assertThat((result as BaseResult.Success).data).hasSize(1)
    }
}
