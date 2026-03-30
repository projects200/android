package com.project200.feature.matching.place

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
class ExercisePlaceSearchViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockGetAddressFromCoordinatesUseCase: GetAddressFromCoordinatesUseCase

    @MockK
    private lateinit var mockGetPlacesByKeywordUseCase: GetPlacesByKeywordUseCase

    private lateinit var viewModel: ExercisePlaceSearchViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val samplePlaceInfo =
        KakaoPlaceInfo(
            placeName = "테스트 장소",
            address = "서울시 강남구",
            latitude = 37.5,
            longitude = 127.0,
        )

    private val sampleSearchResults =
        listOf(
            KakaoPlaceInfo(placeName = "헬스장", address = "서울시 강남구 1", latitude = 37.5, longitude = 127.0),
            KakaoPlaceInfo(placeName = "수영장", address = "서울시 강남구 2", latitude = 37.6, longitude = 127.1),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ExercisePlaceSearchViewModel {
        return ExercisePlaceSearchViewModel(
            getAddressFromCoordinatesUseCase = mockGetAddressFromCoordinatesUseCase,
            searchPlacesByKeywordUseCase = mockGetPlacesByKeywordUseCase,
        )
    }

    @Test
    fun `fetchAddressFromCoordinates - 성공 시 place가 업데이트된다`() =
        runTest {
            coEvery { mockGetAddressFromCoordinatesUseCase(any(), any()) } returns BaseResult.Success(samplePlaceInfo)

            viewModel = createViewModel()
            viewModel.fetchAddressFromCoordinates(37.5, 127.0)
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.place.value?.placeName).isEqualTo(samplePlaceInfo.placeName)
            assertThat(viewModel.place.value?.address).isEqualTo(samplePlaceInfo.address)
        }

    @Test
    fun `fetchAddressFromCoordinates - 성공 시 placeInfoResult가 Success로 업데이트된다`() =
        runTest {
            coEvery { mockGetAddressFromCoordinatesUseCase(any(), any()) } returns BaseResult.Success(samplePlaceInfo)

            viewModel = createViewModel()
            viewModel.fetchAddressFromCoordinates(37.5, 127.0)
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.placeInfoResult.value).isInstanceOf(BaseResult.Success::class.java)
        }

    @Test
    fun `fetchAddressFromCoordinates - 실패 시 place는 업데이트되지 않고 placeInfoResult만 Error로 설정된다`() =
        runTest {
            coEvery { mockGetAddressFromCoordinatesUseCase(any(), any()) } returns BaseResult.Error("ERROR", "조회 실패")

            viewModel = createViewModel()
            viewModel.fetchAddressFromCoordinates(37.5, 127.0)
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.place.value).isNull()
            assertThat(viewModel.placeInfoResult.value).isInstanceOf(BaseResult.Error::class.java)
        }

    @Test
    fun `searchPlacesByKeyword - 검색 결과가 searchResult에 업데이트된다`() =
        runTest {
            coEvery { mockGetPlacesByKeywordUseCase(any(), any(), any()) } returns BaseResult.Success(sampleSearchResults)

            viewModel = createViewModel()
            viewModel.searchPlacesByKeyword("헬스", 37.5, 127.0)
            testDispatcher.scheduler.advanceUntilIdle()

            val result = viewModel.searchResult.value
            assertThat(result).isInstanceOf(BaseResult.Success::class.java)
            assertThat((result as BaseResult.Success).data).hasSize(2)
        }

    @Test
    fun `selectSearchedPlace - placeName이 비어있으면 빈 문자열로 설정된다`() =
        runTest {
            val placeWithEmptyName =
                KakaoPlaceInfo(
                    placeName = "",
                    address = "서울시 강남구",
                    latitude = 37.5,
                    longitude = 127.0,
                )

            viewModel = createViewModel()
            viewModel.selectSearchedPlace(placeWithEmptyName)

            assertThat(viewModel.place.value?.placeName).isEmpty()
        }

    @Test
    fun `selectSearchedPlace - placeName이 address와 같으면 빈 문자열로 설정된다`() =
        runTest {
            val placeWithSameNameAndAddress =
                KakaoPlaceInfo(
                    placeName = "서울시 강남구",
                    address = "서울시 강남구",
                    latitude = 37.5,
                    longitude = 127.0,
                )

            viewModel = createViewModel()
            viewModel.selectSearchedPlace(placeWithSameNameAndAddress)

            assertThat(viewModel.place.value?.placeName).isEmpty()
        }

    @Test
    fun `selectSearchedPlace - 유효한 placeName이면 그대로 설정된다`() =
        runTest {
            viewModel = createViewModel()
            viewModel.selectSearchedPlace(samplePlaceInfo)

            assertThat(viewModel.place.value?.placeName).isEqualTo(samplePlaceInfo.placeName)
        }

    @Test
    fun `onMapMoved - selectSearchedPlace 직후에는 API를 호출하지 않는다`() =
        runTest {
            coEvery { mockGetAddressFromCoordinatesUseCase(any(), any()) } returns BaseResult.Success(samplePlaceInfo)

            viewModel = createViewModel()
            viewModel.selectSearchedPlace(samplePlaceInfo)
            viewModel.onMapMoved(37.6, 127.1)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) { mockGetAddressFromCoordinatesUseCase(any(), any()) }
        }

    @Test
    fun `onMapMoved - 선택 없이 이동하면 fetchAddressFromCoordinates가 호출된다`() =
        runTest {
            coEvery { mockGetAddressFromCoordinatesUseCase(any(), any()) } returns BaseResult.Success(samplePlaceInfo)

            viewModel = createViewModel()
            viewModel.onMapMoved(37.5, 127.0)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { mockGetAddressFromCoordinatesUseCase(37.5, 127.0) }
        }

    @Test
    fun `onMapMoved - selectSearchedPlace 후 두번째 이동부터는 API가 호출된다`() =
        runTest {
            coEvery { mockGetAddressFromCoordinatesUseCase(any(), any()) } returns BaseResult.Success(samplePlaceInfo)

            viewModel = createViewModel()
            viewModel.selectSearchedPlace(samplePlaceInfo)
            viewModel.onMapMoved(37.6, 127.1)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onMapMoved(37.7, 127.2)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { mockGetAddressFromCoordinatesUseCase(37.7, 127.2) }
        }
}
