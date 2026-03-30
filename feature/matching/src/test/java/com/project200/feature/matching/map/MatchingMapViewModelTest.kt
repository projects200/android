package com.project200.feature.matching.map

import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kakao.vectormap.LatLng
import com.project200.common.utils.ClockProvider
import com.project200.domain.model.AgeGroup
import com.project200.domain.model.BaseResult
import com.project200.domain.model.DayOfWeek
import com.project200.domain.model.ExercisePlace
import com.project200.domain.model.ExerciseType
import com.project200.domain.model.Location
import com.project200.domain.model.MapBounds
import com.project200.domain.model.MapPosition
import com.project200.domain.model.MatchingMember
import com.project200.domain.model.PreferredExercise
import com.project200.domain.usecase.GetExercisePlaceUseCase
import com.project200.domain.usecase.GetExerciseTypesUseCase
import com.project200.domain.usecase.GetLastMapPositionUseCase
import com.project200.domain.usecase.GetMatchingMembersUseCase
import com.project200.domain.usecase.SaveLastMapPositionUseCase
import com.project200.feature.matching.utils.MatchingFilterType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
import java.time.LocalDate

@ExperimentalCoroutinesApi
class MatchingMapViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockGetMatchingMembersUseCase: GetMatchingMembersUseCase

    @MockK
    private lateinit var mockGetLastMapPositionUseCase: GetLastMapPositionUseCase

    @MockK
    private lateinit var mockSaveLastMapPositionUseCase: SaveLastMapPositionUseCase

    @MockK
    private lateinit var mockGetExercisePlaceUseCase: GetExercisePlaceUseCase

    @MockK
    private lateinit var mockGetExerciseTypesUseCase: GetExerciseTypesUseCase

    @MockK
    private lateinit var mockClockProvider: ClockProvider

    @MockK(relaxed = true)
    private lateinit var mockSharedPreferences: SharedPreferences

    private lateinit var viewModel: MatchingMapViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val now = LocalDate.of(2025, 1, 15)

    private val sampleMapPosition = MapPosition(
        latitude = 37.5665,
        longitude = 126.9780,
        zoomLevel = 15
    )

    private val sampleExercisePlaces = listOf(
        ExercisePlace(id = 1L, name = "헬스장", address = "서울시 강남구", latitude = 37.5, longitude = 127.0),
        ExercisePlace(id = 2L, name = "수영장", address = "서울시 서초구", latitude = 37.4, longitude = 127.1)
    )

    private val sampleExerciseTypes = listOf(
        ExerciseType(id = 1L, name = "헬스", imageUrl = "https://example.com/health.jpg"),
        ExerciseType(id = 2L, name = "수영", imageUrl = "https://example.com/swimming.jpg")
    )

    private val sampleMatchingMembers = listOf(
        MatchingMember(
            memberId = "member1",
            profileThumbnailUrl = "https://example.com/thumb1.jpg",
            profileImageUrl = "https://example.com/1.jpg",
            nickname = "유저1",
            gender = "MALE",
            birthDate = "1990-01-01",
            memberScore = 80,
            locations = listOf(
                Location(placeId = 1L, placeName = "헬스장", latitude = 37.5, longitude = 127.0)
            ),
            preferredExercises = listOf(
                PreferredExercise(
                    preferredExerciseId = 1L,
                    exerciseTypeId = 1L,
                    name = "헬스",
                    skillLevel = "BEGINNER",
                    daysOfWeek = listOf(true, false, true, false, true, false, false),
                    imageUrl = "https://example.com/health.jpg"
                )
            )
        ),
        MatchingMember(
            memberId = "member2",
            profileThumbnailUrl = "https://example.com/thumb2.jpg",
            profileImageUrl = "https://example.com/2.jpg",
            nickname = "유저2",
            gender = "FEMALE",
            birthDate = "1995-05-15",
            memberScore = 90,
            locations = listOf(
                Location(placeId = 2L, placeName = "수영장", latitude = 37.6, longitude = 127.1)
            ),
            preferredExercises = listOf(
                PreferredExercise(
                    preferredExerciseId = 2L,
                    exerciseTypeId = 2L,
                    name = "수영",
                    skillLevel = "INTERMEDIATE",
                    daysOfWeek = listOf(false, true, false, true, false, false, false),
                    imageUrl = "https://example.com/swimming.jpg"
                )
            )
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockClockProvider.now() } returns now
        every { mockSharedPreferences.getBoolean(any(), any()) } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel = MatchingMapViewModel(
            getMatchingMembersUseCase = mockGetMatchingMembersUseCase,
            getLastMapPositionUseCase = mockGetLastMapPositionUseCase,
            saveLastMapPositionUseCase = mockSaveLastMapPositionUseCase,
            getExercisePlaceUseCase = mockGetExercisePlaceUseCase,
            getExerciseTypesUseCase = mockGetExerciseTypesUseCase,
            clockProvider = mockClockProvider,
            sharedPreferences = mockSharedPreferences
        )
    }

    @Test
    fun `init - 초기 지도 위치가 로드된다`() = runTest {
        coEvery { mockGetLastMapPositionUseCase() } returns sampleMapPosition
        coEvery { mockGetExercisePlaceUseCase() } returns BaseResult.Success(sampleExercisePlaces)
        coEvery { mockGetExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.initialMapPosition.value).isEqualTo(sampleMapPosition)
    }

    @Test
    fun `init - 운동 종류 목록이 로드된다`() = runTest {
        coEvery { mockGetLastMapPositionUseCase() } returns sampleMapPosition
        coEvery { mockGetExercisePlaceUseCase() } returns BaseResult.Success(sampleExercisePlaces)
        coEvery { mockGetExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.exerciseTypes.value).isEqualTo(sampleExerciseTypes)
    }

    @Test
    fun `init - 최초 방문 시 가이드 이벤트가 emit된다`() = runTest {
        every { mockSharedPreferences.getBoolean(any(), any()) } returns true
        coEvery { mockGetLastMapPositionUseCase() } returns sampleMapPosition
        coEvery { mockGetExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)

        createViewModel()

        viewModel.shouldShowGuide.test {
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(Unit)
        }
    }

    @Test
    fun `init - 운동 장소 없을 시 다이얼로그 이벤트가 emit된다`() = runTest {
        coEvery { mockGetLastMapPositionUseCase() } returns sampleMapPosition
        coEvery { mockGetExercisePlaceUseCase() } returns BaseResult.Success(emptyList())
        coEvery { mockGetExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)

        createViewModel()

        viewModel.shouldShowPlaceGuideDialog.test {
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(Unit)
        }
    }

    @Test
    fun `saveLastLocation - 위치가 저장된다`() = runTest {
        coEvery { mockGetLastMapPositionUseCase() } returns sampleMapPosition
        coEvery { mockGetExercisePlaceUseCase() } returns BaseResult.Success(sampleExercisePlaces)
        coEvery { mockGetExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)
        coEvery { mockSaveLastMapPositionUseCase(any()) } returns Unit

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveLastLocation(37.5, 127.0, 14)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockSaveLastMapPositionUseCase(MapPosition(37.5, 127.0, 14)) }
    }

    @Test
    fun `refreshExercisePlaces - 운동 장소가 새로고침된다`() = runTest {
        coEvery { mockGetLastMapPositionUseCase() } returns sampleMapPosition
        coEvery { mockGetExercisePlaceUseCase() } returns BaseResult.Success(sampleExercisePlaces)
        coEvery { mockGetExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refreshExercisePlaces()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(atLeast = 2) { mockGetExercisePlaceUseCase() }
    }

    @Test
    fun `onFilterTypeClicked - 필터 타입이 emit된다`() = runTest {
        coEvery { mockGetLastMapPositionUseCase() } returns sampleMapPosition
        coEvery { mockGetExercisePlaceUseCase() } returns BaseResult.Success(sampleExercisePlaces)
        coEvery { mockGetExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.currentFilterType.test {
            viewModel.onFilterTypeClicked(MatchingFilterType.GENDER)
            assertThat(awaitItem()).isEqualTo(MatchingFilterType.GENDER)
        }
    }

    @Test
    fun `clearFilters - 필터 상태가 초기화된다`() = runTest {
        coEvery { mockGetLastMapPositionUseCase() } returns sampleMapPosition
        coEvery { mockGetExercisePlaceUseCase() } returns BaseResult.Success(sampleExercisePlaces)
        coEvery { mockGetExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.clearFilters()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.filterState.value.gender).isNull()
        assertThat(viewModel.filterState.value.ageGroup).isNull()
        assertThat(viewModel.filterState.value.skillLevel).isNull()
    }

    @Test
    fun `onFilterOptionSelected - DAY 필터가 토글된다`() = runTest {
        coEvery { mockGetLastMapPositionUseCase() } returns sampleMapPosition
        coEvery { mockGetExercisePlaceUseCase() } returns BaseResult.Success(sampleExercisePlaces)
        coEvery { mockGetExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFilterOptionSelected(MatchingFilterType.DAY, DayOfWeek.MONDAY)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.filterState.value.days).contains(DayOfWeek.MONDAY)

        viewModel.onFilterOptionSelected(MatchingFilterType.DAY, DayOfWeek.MONDAY)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.filterState.value.days).doesNotContain(DayOfWeek.MONDAY)
    }

    @Test
    fun `onFilterOptionSelected - EXERCISE_TYPE 필터가 설정된다`() = runTest {
        coEvery { mockGetLastMapPositionUseCase() } returns sampleMapPosition
        coEvery { mockGetExercisePlaceUseCase() } returns BaseResult.Success(sampleExercisePlaces)
        coEvery { mockGetExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFilterOptionSelected(MatchingFilterType.EXERCISE_TYPE, sampleExerciseTypes[0])
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.filterState.value.selectedExerciseType?.id).isEqualTo(1L)
    }

    @Test
    fun `fetchMatchingMembersIfMoved - 줌 레벨 낮으면 회원 목록 비우고 경고 emit`() = runTest {
        coEvery { mockGetLastMapPositionUseCase() } returns sampleMapPosition
        coEvery { mockGetExercisePlaceUseCase() } returns BaseResult.Success(sampleExercisePlaces)
        coEvery { mockGetExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val lowZoomBounds = MapBounds(37.6, 126.9, 37.5, 127.0)
        val center = LatLng.from(37.55, 126.95)

        viewModel.zoomLevelWarning.test {
            viewModel.fetchMatchingMembersIfMoved(lowZoomBounds, center, 10)
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(Unit)
        }
    }

    @Test
    fun `fetchMatchingMembersIfMoved - 충분히 이동 시 회원 목록 조회`() = runTest {
        coEvery { mockGetLastMapPositionUseCase() } returns sampleMapPosition
        coEvery { mockGetExercisePlaceUseCase() } returns BaseResult.Success(sampleExercisePlaces)
        coEvery { mockGetExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)
        coEvery { mockGetMatchingMembersUseCase(any()) } returns BaseResult.Success(sampleMatchingMembers)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val bounds = MapBounds(37.6, 126.9, 37.5, 127.0)
        val center = LatLng.from(37.55, 126.95)

        viewModel.fetchMatchingMembersIfMoved(bounds, center, 15)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockGetMatchingMembersUseCase(bounds) }
    }

    @Test
    fun `fetchMatchingMembersIfMoved - 작은 이동은 조회하지 않음`() = runTest {
        coEvery { mockGetLastMapPositionUseCase() } returns sampleMapPosition
        coEvery { mockGetExercisePlaceUseCase() } returns BaseResult.Success(sampleExercisePlaces)
        coEvery { mockGetExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)
        coEvery { mockGetMatchingMembersUseCase(any()) } returns BaseResult.Success(sampleMatchingMembers)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val bounds1 = MapBounds(37.6, 126.9, 37.5, 127.0)
        val center1 = LatLng.from(37.55, 126.95)
        viewModel.fetchMatchingMembersIfMoved(bounds1, center1, 15)
        testDispatcher.scheduler.advanceUntilIdle()

        val bounds2 = MapBounds(37.601, 126.901, 37.501, 127.001)
        val center2 = LatLng.from(37.551, 126.951)
        viewModel.fetchMatchingMembersIfMoved(bounds2, center2, 15)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockGetMatchingMembersUseCase(any()) }
    }
}
