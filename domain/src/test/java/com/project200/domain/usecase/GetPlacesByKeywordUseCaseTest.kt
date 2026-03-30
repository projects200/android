package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.KakaoPlaceInfo
import com.project200.domain.repository.AddressRepository
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
class GetPlacesByKeywordUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: AddressRepository

    private lateinit var useCase: GetPlacesByKeywordUseCase

    private val sampleQuery = "헬스장"
    private val sampleLatitude = 37.5665
    private val sampleLongitude = 126.9780

    private val samplePlaces = listOf(
        KakaoPlaceInfo(
            placeName = "강남 피트니스",
            address = "서울시 강남구 역삼동 123",
            latitude = 37.500,
            longitude = 127.036
        ),
        KakaoPlaceInfo(
            placeName = "역삼 헬스클럽",
            address = "서울시 강남구 역삼동 456",
            latitude = 37.501,
            longitude = 127.037
        )
    )

    @Before
    fun setUp() {
        useCase = GetPlacesByKeywordUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 장소 목록 성공적으로 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(samplePlaces)
        coEvery {
            mockRepository.getPlacesByKeyword(sampleQuery, sampleLatitude, sampleLongitude)
        } returns successResult

        // When
        val result = useCase(sampleQuery, sampleLatitude, sampleLongitude)

        // Then
        coVerify(exactly = 1) {
            mockRepository.getPlacesByKeyword(sampleQuery, sampleLatitude, sampleLongitude)
        }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data).hasSize(2)
    }

    @Test
    fun `빈 검색 결과 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(emptyList<KakaoPlaceInfo>())
        coEvery {
            mockRepository.getPlacesByKeyword("존재하지않는장소", sampleLatitude, sampleLongitude)
        } returns successResult

        // When
        val result = useCase("존재하지않는장소", sampleLatitude, sampleLongitude)

        // Then
        assertThat((result as BaseResult.Success).data).isEmpty()
    }

    @Test
    fun `장소 검색 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("API_ERROR", "Kakao API error")
        coEvery {
            mockRepository.getPlacesByKeyword(sampleQuery, sampleLatitude, sampleLongitude)
        } returns errorResult

        // When
        val result = useCase(sampleQuery, sampleLatitude, sampleLongitude)

        // Then
        assertThat(result).isEqualTo(errorResult)
        assertThat((result as BaseResult.Error).message).isEqualTo("Kakao API error")
    }

    @Test
    fun `다른 키워드로 검색`() = runTest {
        // Given
        val pilatesPlaces = listOf(
            KakaoPlaceInfo(
                placeName = "강남 필라테스",
                address = "서울시 강남구 논현동 789",
                latitude = 37.510,
                longitude = 127.025
            )
        )
        coEvery {
            mockRepository.getPlacesByKeyword("필라테스", sampleLatitude, sampleLongitude)
        } returns BaseResult.Success(pilatesPlaces)

        // When
        val result = useCase("필라테스", sampleLatitude, sampleLongitude)

        // Then
        assertThat((result as BaseResult.Success).data).hasSize(1)
        assertThat(result.data[0].placeName).contains("필라테스")
    }

    @Test
    fun `다른 위치에서 검색`() = runTest {
        // Given
        val busanLatitude = 35.1796
        val busanLongitude = 129.0756
        val busanPlaces = listOf(
            KakaoPlaceInfo(
                placeName = "해운대 헬스장",
                address = "부산시 해운대구 123",
                latitude = 35.158,
                longitude = 129.160
            )
        )
        coEvery {
            mockRepository.getPlacesByKeyword(sampleQuery, busanLatitude, busanLongitude)
        } returns BaseResult.Success(busanPlaces)

        // When
        val result = useCase(sampleQuery, busanLatitude, busanLongitude)

        // Then
        coVerify(exactly = 1) {
            mockRepository.getPlacesByKeyword(sampleQuery, busanLatitude, busanLongitude)
        }
        assertThat((result as BaseResult.Success).data[0].placeName).contains("해운대")
    }
}
