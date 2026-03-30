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
class GetAddressFromCoordinatesUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: AddressRepository

    private lateinit var useCase: GetAddressFromCoordinatesUseCase

    private val sampleLatitude = 37.5665
    private val sampleLongitude = 126.9780

    private val samplePlaceInfo = KakaoPlaceInfo(
        placeName = "서울시청",
        address = "서울특별시 중구 태평로1가 31",
        latitude = sampleLatitude,
        longitude = sampleLongitude
    )

    @Before
    fun setUp() {
        useCase = GetAddressFromCoordinatesUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 주소 정보 성공적으로 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(samplePlaceInfo)
        coEvery {
            mockRepository.getAddressFromCoordinates(sampleLatitude, sampleLongitude)
        } returns successResult

        // When
        val result = useCase(sampleLatitude, sampleLongitude)

        // Then
        coVerify(exactly = 1) {
            mockRepository.getAddressFromCoordinates(sampleLatitude, sampleLongitude)
        }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data.address).contains("서울")
    }

    @Test
    fun `좌표 변환 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("API_ERROR", "Failed to convert coordinates")
        coEvery {
            mockRepository.getAddressFromCoordinates(sampleLatitude, sampleLongitude)
        } returns errorResult

        // When
        val result = useCase(sampleLatitude, sampleLongitude)

        // Then
        assertThat(result).isEqualTo(errorResult)
        assertThat((result as BaseResult.Error).message).isEqualTo("Failed to convert coordinates")
    }

    @Test
    fun `다른 지역 좌표 변환`() = runTest {
        // Given
        val busanLatitude = 35.1796
        val busanLongitude = 129.0756
        val busanPlaceInfo = KakaoPlaceInfo(
            placeName = "부산시청",
            address = "부산광역시 연제구 연산동 1000",
            latitude = busanLatitude,
            longitude = busanLongitude
        )
        coEvery {
            mockRepository.getAddressFromCoordinates(busanLatitude, busanLongitude)
        } returns BaseResult.Success(busanPlaceInfo)

        // When
        val result = useCase(busanLatitude, busanLongitude)

        // Then
        assertThat((result as BaseResult.Success).data.address).contains("부산")
    }

    @Test
    fun `제주도 좌표 변환`() = runTest {
        // Given
        val jejuLatitude = 33.4996
        val jejuLongitude = 126.5312
        val jejuPlaceInfo = KakaoPlaceInfo(
            placeName = "제주시청",
            address = "제주특별자치도 제주시 이도2동",
            latitude = jejuLatitude,
            longitude = jejuLongitude
        )
        coEvery {
            mockRepository.getAddressFromCoordinates(jejuLatitude, jejuLongitude)
        } returns BaseResult.Success(jejuPlaceInfo)

        // When
        val result = useCase(jejuLatitude, jejuLongitude)

        // Then
        assertThat((result as BaseResult.Success).data.address).contains("제주")
    }

    @Test
    fun `유효하지 않은 좌표로 조회 시 에러 반환`() = runTest {
        // Given
        val invalidLatitude = 999.0
        val invalidLongitude = 999.0
        val errorResult = BaseResult.Error("INVALID_COORDS", "Invalid coordinates")
        coEvery {
            mockRepository.getAddressFromCoordinates(invalidLatitude, invalidLongitude)
        } returns errorResult

        // When
        val result = useCase(invalidLatitude, invalidLongitude)

        // Then
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("INVALID_COORDS")
    }
}
