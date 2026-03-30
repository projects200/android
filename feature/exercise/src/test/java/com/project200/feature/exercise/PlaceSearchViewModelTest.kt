package com.project200.feature.exercise.form

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.KakaoPlaceInfo
import com.project200.domain.usecase.GetAddressFromCoordinatesUseCase
import com.project200.domain.usecase.GetPlacesByKeywordUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class PlaceSearchViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockGetAddressFromCoordinatesUseCase: GetAddressFromCoordinatesUseCase

    @MockK
    private lateinit var mockGetPlacesByKeywordUseCase: GetPlacesByKeywordUseCase

    private lateinit var viewModel: PlaceSearchViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val samplePlaceInfo = KakaoPlaceInfo(
        placeName = "테스트 장소",
        address = "서울시 강남구 역삼동",
        latitude = 37.5665,
        longitude = 126.9780
    )

    private val sampleSearchResults = listOf(
        KakaoPlaceInfo(
            placeName = "헬스장 A",
            address = "서울시 강남구 역삼동 123",
            latitude = 37.5001,
            longitude = 127.0001
        ),
        KakaoPlaceInfo(
            placeName = "헬스장 B",
            address = "서울시 강남구 삼성동 456",
            latitude = 37.5002,
            longitude = 127.0002
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PlaceSearchViewModel {
        return PlaceSearchViewModel(
            getAddressFromCoordinatesUseCase = mockGetAddressFromCoordinatesUseCase,
            searchPlacesByKeywordUseCase = mockGetPlacesByKeywordUseCase
        )
    }

    @Test
    fun `fetchAddressFromCoordinates - 성공하면 place와 placeInfoResult가 업데이트된다`() = runTest {
        // Given
        coEvery { mockGetAddressFromCoordinatesUseCase(any(), any()) } returns BaseResult.Success(samplePlaceInfo)

        viewModel = createViewModel()

        // When
        viewModel.fetchAddressFromCoordinates(37.5665, 126.9780)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.place.value).isNotNull()
        assertThat(viewModel.place.value?.placeName).isEqualTo(samplePlaceInfo.placeName)
        assertThat(viewModel.place.value?.address).isEqualTo(samplePlaceInfo.address)
        assertThat(viewModel.placeInfoResult.value).isInstanceOf(BaseResult.Success::class.java)
    }

    @Test
    fun `fetchAddressFromCoordinates - 성공하면 좌표가 정확히 설정된다`() = runTest {
        // Given
        val latitude = 37.1234
        val longitude = 127.5678
        coEvery { mockGetAddressFromCoordinatesUseCase(latitude, longitude) } returns BaseResult.Success(samplePlaceInfo)

        viewModel = createViewModel()

        // When
        viewModel.fetchAddressFromCoordinates(latitude, longitude)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.place.value?.latitude).isEqualTo(latitude)
        assertThat(viewModel.place.value?.longitude).isEqualTo(longitude)
    }

    @Test
    fun `fetchAddressFromCoordinates - 실패하면 placeInfoResult에 에러가 설정된다`() = runTest {
        // Given
        coEvery { mockGetAddressFromCoordinatesUseCase(any(), any()) } returns BaseResult.Error("ERROR", "주소 조회 실패")

        viewModel = createViewModel()

        // When
        viewModel.fetchAddressFromCoordinates(37.5665, 126.9780)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.placeInfoResult.value).isInstanceOf(BaseResult.Error::class.java)
    }

    @Test
    fun `searchPlacesByKeyword - 성공하면 검색 결과가 반환된다`() = runTest {
        // Given
        coEvery { mockGetPlacesByKeywordUseCase(any(), any(), any()) } returns BaseResult.Success(sampleSearchResults)

        viewModel = createViewModel()

        // When
        viewModel.searchPlacesByKeyword("헬스장", 37.5665, 126.9780)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val result = viewModel.searchResult.value
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
        assertThat((result as BaseResult.Success).data).hasSize(2)
    }

    @Test
    fun `searchPlacesByKeyword - 실패하면 에러가 반환된다`() = runTest {
        // Given
        coEvery { mockGetPlacesByKeywordUseCase(any(), any(), any()) } returns BaseResult.Error("ERROR", "검색 실패")

        viewModel = createViewModel()

        // When
        viewModel.searchPlacesByKeyword("헬스장", 37.5665, 126.9780)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.searchResult.value).isInstanceOf(BaseResult.Error::class.java)
    }

    @Test
    fun `selectSearchedPlace - 장소 선택 시 place가 업데이트된다`() = runTest {
        // Given
        viewModel = createViewModel()

        // When
        viewModel.selectSearchedPlace(sampleSearchResults[0])

        // Then
        assertThat(viewModel.place.value).isEqualTo(sampleSearchResults[0])
    }

    @Test
    fun `selectSearchedPlace - placeName이 비어있으면 그대로 설정된다`() = runTest {
        // Given
        viewModel = createViewModel()
        val placeWithEmptyName = KakaoPlaceInfo(
            placeName = "",
            address = "서울시 강남구",
            latitude = 37.5,
            longitude = 127.0
        )

        // When
        viewModel.selectSearchedPlace(placeWithEmptyName)

        // Then
        assertThat(viewModel.place.value?.placeName).isEmpty()
    }

    @Test
    fun `selectSearchedPlace - placeName이 address와 같으면 placeName을 빈 문자열로 설정한다`() = runTest {
        // Given
        viewModel = createViewModel()
        val placeWithSameName = KakaoPlaceInfo(
            placeName = "서울시 강남구",
            address = "서울시 강남구",
            latitude = 37.5,
            longitude = 127.0
        )

        // When
        viewModel.selectSearchedPlace(placeWithSameName)

        // Then
        assertThat(viewModel.place.value?.placeName).isEmpty()
    }

    @Test
    fun `onMapMoved - 검색으로 선택한 직후에는 주소 조회를 하지 않는다`() = runTest {
        // Given
        coEvery { mockGetAddressFromCoordinatesUseCase(any(), any()) } returns BaseResult.Success(samplePlaceInfo)

        viewModel = createViewModel()
        viewModel.selectSearchedPlace(sampleSearchResults[0])

        // When
        viewModel.onMapMoved(37.5, 127.0)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 0) { mockGetAddressFromCoordinatesUseCase(any(), any()) }
    }

    @Test
    fun `onMapMoved - 일반 지도 이동 시 주소 조회를 수행한다`() = runTest {
        // Given
        coEvery { mockGetAddressFromCoordinatesUseCase(any(), any()) } returns BaseResult.Success(samplePlaceInfo)

        viewModel = createViewModel()

        // When
        viewModel.onMapMoved(37.5, 127.0)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockGetAddressFromCoordinatesUseCase(37.5, 127.0) }
    }

    @Test
    fun `onMapMoved - 검색 선택 후 두 번째 이동부터는 주소 조회를 수행한다`() = runTest {
        // Given
        coEvery { mockGetAddressFromCoordinatesUseCase(any(), any()) } returns BaseResult.Success(samplePlaceInfo)

        viewModel = createViewModel()
        viewModel.selectSearchedPlace(sampleSearchResults[0])

        // When
        viewModel.onMapMoved(37.5, 127.0)
        viewModel.onMapMoved(37.6, 127.1)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockGetAddressFromCoordinatesUseCase(37.6, 127.1) }
    }
}
