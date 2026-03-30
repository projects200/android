package com.project200.domain.usecase

import com.project200.domain.model.MapPosition
import com.project200.domain.repository.MatchingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class SaveLastMapPositionUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MatchingRepository

    private lateinit var useCase: SaveLastMapPositionUseCase

    private val sampleMapPosition = MapPosition(
        latitude = 37.5665,
        longitude = 126.9780,
        zoomLevel = 15
    )

    @Before
    fun setUp() {
        useCase = SaveLastMapPositionUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 지도 위치 저장`() = runTest {
        // Given
        coEvery { mockRepository.saveLastMapPosition(sampleMapPosition) } just runs

        // When
        useCase(sampleMapPosition)

        // Then
        coVerify(exactly = 1) { mockRepository.saveLastMapPosition(sampleMapPosition) }
    }

    @Test
    fun `다른 위치로 저장`() = runTest {
        // Given
        val differentPosition = MapPosition(
            latitude = 35.1796,
            longitude = 129.0756,
            zoomLevel = 12
        )
        coEvery { mockRepository.saveLastMapPosition(differentPosition) } just runs

        // When
        useCase(differentPosition)

        // Then
        coVerify(exactly = 1) { mockRepository.saveLastMapPosition(differentPosition) }
    }

    @Test
    fun `최대 줌 레벨로 저장`() = runTest {
        // Given
        val maxZoomPosition = sampleMapPosition.copy(zoomLevel = 21)
        coEvery { mockRepository.saveLastMapPosition(maxZoomPosition) } just runs

        // When
        useCase(maxZoomPosition)

        // Then
        coVerify(exactly = 1) { mockRepository.saveLastMapPosition(maxZoomPosition) }
    }

    @Test
    fun `최소 줌 레벨로 저장`() = runTest {
        // Given
        val minZoomPosition = sampleMapPosition.copy(zoomLevel = 1)
        coEvery { mockRepository.saveLastMapPosition(minZoomPosition) } just runs

        // When
        useCase(minZoomPosition)

        // Then
        coVerify(exactly = 1) { mockRepository.saveLastMapPosition(minZoomPosition) }
    }
}
