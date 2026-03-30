package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.MapPosition
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
class GetLastMapPositionUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MatchingRepository

    private lateinit var useCase: GetLastMapPositionUseCase

    private val sampleMapPosition = MapPosition(
        latitude = 37.5665,
        longitude = 126.9780,
        zoomLevel = 15
    )

    @Before
    fun setUp() {
        useCase = GetLastMapPositionUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 저장된 지도 위치 반환`() = runTest {
        // Given
        coEvery { mockRepository.getLastMapPosition() } returns sampleMapPosition

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.getLastMapPosition() }
        assertThat(result).isEqualTo(sampleMapPosition)
        assertThat(result?.latitude).isEqualTo(37.5665)
        assertThat(result?.longitude).isEqualTo(126.9780)
        assertThat(result?.zoomLevel).isEqualTo(15)
    }

    @Test
    fun `저장된 위치가 없을 때 null 반환`() = runTest {
        // Given
        coEvery { mockRepository.getLastMapPosition() } returns null

        // When
        val result = useCase()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `다른 지역의 저장된 위치 반환`() = runTest {
        // Given
        val busanPosition = MapPosition(
            latitude = 35.1796,
            longitude = 129.0756,
            zoomLevel = 12
        )
        coEvery { mockRepository.getLastMapPosition() } returns busanPosition

        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(busanPosition)
        assertThat(result?.zoomLevel).isEqualTo(12)
    }
}
