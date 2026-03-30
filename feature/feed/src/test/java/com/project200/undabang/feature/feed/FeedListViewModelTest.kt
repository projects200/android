package com.project200.undabang.feature.feed

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseType
import com.project200.domain.model.Feed
import com.project200.domain.model.FeedListResult
import com.project200.domain.model.PreferredExercise
import com.project200.domain.usecase.DeleteFeedUseCase
import com.project200.domain.usecase.GetFeedsUseCase
import com.project200.domain.usecase.GetMemberIdUseCase
import com.project200.domain.usecase.GetPreferredExerciseTypesUseCase
import com.project200.domain.usecase.GetPreferredExerciseUseCase
import com.project200.undabang.feature.feed.list.FeedListViewModel
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
import java.time.LocalDateTime

@ExperimentalCoroutinesApi
class FeedListViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var getFeedsUseCase: GetFeedsUseCase

    @MockK
    private lateinit var getPreferredExerciseUseCase: GetPreferredExerciseUseCase

    @MockK
    private lateinit var getPreferredExerciseTypesUseCase: GetPreferredExerciseTypesUseCase

    @MockK
    private lateinit var getMemberIdUseCase: GetMemberIdUseCase

    @MockK
    private lateinit var deleteFeedUseCase: DeleteFeedUseCase

    private lateinit var viewModel: FeedListViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val sampleExerciseType =
        ExerciseType(
            id = 1L,
            name = "헬스",
            imageUrl = null,
        )

    private val sampleFeed =
        Feed(
            feedId = 1L,
            memberId = "member1",
            nickname = "테스터",
            feedTypeName = "헬스",
            feedTypeId = 1L,
            feedTypeDesc = "헬스 운동",
            feedContent = "테스트 피드",
            feedPictures = emptyList(),
            feedLikesCount = 0,
            feedCommentsCount = 0,
            feedIsLiked = false,
            feedCreatedAt = LocalDateTime.of(2025, 1, 1, 10, 0, 0),
            feedHasCommented = false,
            thumbnailUrl = null,
            profileUrl = null,
        )

    private val sampleFeedListResult =
        FeedListResult(
            feeds = listOf(sampleFeed),
            hasNext = false,
        )

    private val samplePreferredExercise =
        PreferredExercise(
            preferredExerciseId = 1L,
            exerciseTypeId = 1L,
            name = "헬스",
            skillLevel = "BEGINNER",
            daysOfWeek = List(7) { false },
            imageUrl = null,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): FeedListViewModel {
        coEvery { getFeedsUseCase(any(), any()) } returns BaseResult.Success(sampleFeedListResult)
        coEvery { getPreferredExerciseUseCase() } returns BaseResult.Success(listOf(samplePreferredExercise))
        coEvery { getPreferredExerciseTypesUseCase() } returns BaseResult.Success(listOf(samplePreferredExercise))
        coEvery { getMemberIdUseCase() } returns "member1"

        return FeedListViewModel(
            getFeedsUseCase,
            getPreferredExerciseUseCase,
            getPreferredExerciseTypesUseCase,
            getMemberIdUseCase,
            deleteFeedUseCase,
        )
    }

    @Test
    fun `init - ViewModel 생성 시 피드 목록을 로드한다`() =
        runTest {
            // When
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.feedList.value).isNotNull()
            assertThat(viewModel.feedList.value).hasSize(1)
            coVerify { getFeedsUseCase(null, 10) }
        }

    @Test
    fun `init - ViewModel 생성 시 운동 타입을 로드한다`() =
        runTest {
            // When
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.exerciseTypeList.value).isNotNull()
            coVerify { getPreferredExerciseUseCase() }
            coVerify { getPreferredExerciseTypesUseCase() }
        }

    @Test
    fun `init - ViewModel 생성 시 현재 회원 ID를 로드한다`() =
        runTest {
            // When
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.currentMemberId.value).isEqualTo("member1")
        }

    @Test
    fun `selectType - 타입 선택 시 필터가 적용된다`() =
        runTest {
            // Given
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            viewModel.selectType(sampleExerciseType)

            // Then
            assertThat(viewModel.selectedType.value).isEqualTo(sampleExerciseType)
        }

    @Test
    fun `selectType - feedTypeId로 필터링하여 목록을 반환한다`() =
        runTest {
            // Given
            val feed1 = sampleFeed.copy(feedId = 1L, feedTypeId = 1L)
            val feed2 = sampleFeed.copy(feedId = 2L, feedTypeId = 2L)
            coEvery { getFeedsUseCase(any(), any()) } returns
                BaseResult.Success(
                    FeedListResult(feeds = listOf(feed1, feed2), hasNext = false),
                )
            coEvery { getPreferredExerciseUseCase() } returns BaseResult.Success(listOf(samplePreferredExercise))
            coEvery { getPreferredExerciseTypesUseCase() } returns BaseResult.Success(listOf(samplePreferredExercise))
            coEvery { getMemberIdUseCase() } returns "member1"

            viewModel =
                FeedListViewModel(
                    getFeedsUseCase,
                    getPreferredExerciseUseCase,
                    getPreferredExerciseTypesUseCase,
                    getMemberIdUseCase,
                    deleteFeedUseCase,
                )
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            viewModel.selectType(sampleExerciseType)

            // Then
            assertThat(viewModel.feedList.value).hasSize(1)
            assertThat(viewModel.feedList.value?.first()?.feedTypeId).isEqualTo(1L)
        }

    @Test
    fun `clearType - 타입 초기화 시 필터가 해제된다`() =
        runTest {
            // Given
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.selectType(sampleExerciseType)

            // When
            viewModel.clearType()

            // Then
            assertThat(viewModel.selectedType.value).isNull()
        }

    @Test
    fun `canLoadMore - 로딩 중이 아니고 다음 페이지가 있으면 true`() =
        runTest {
            // Given
            coEvery { getPreferredExerciseUseCase() } returns BaseResult.Success(listOf(samplePreferredExercise))
            coEvery { getPreferredExerciseTypesUseCase() } returns BaseResult.Success(listOf(samplePreferredExercise))
            coEvery { getMemberIdUseCase() } returns "member1"
            coEvery { getFeedsUseCase(any(), any()) } returns
                BaseResult.Success(
                    FeedListResult(feeds = listOf(sampleFeed), hasNext = true),
                )
            viewModel =
                FeedListViewModel(
                    getFeedsUseCase,
                    getPreferredExerciseUseCase,
                    getPreferredExerciseTypesUseCase,
                    getMemberIdUseCase,
                    deleteFeedUseCase,
                )
            testDispatcher.scheduler.advanceUntilIdle()

            // When & Then
            assertThat(viewModel.canLoadMore()).isTrue()
        }

    @Test
    fun `canLoadMore - 타입 필터가 적용되면 false`() =
        runTest {
            // Given
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            viewModel.selectType(sampleExerciseType)

            // Then
            assertThat(viewModel.canLoadMore()).isFalse()
        }

    @Test
    fun `loadFeeds - 새로고침 시 목록을 초기화하고 다시 로드한다`() =
        runTest {
            // Given
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            viewModel.loadFeeds(isRefresh = true)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(atLeast = 2) { getFeedsUseCase(null, 10) }
        }

    @Test
    fun `loadFeeds - 에러 발생 시 토스트 이벤트를 발생시킨다`() =
        runTest {
            // Given
            coEvery { getFeedsUseCase(any(), any()) } returns BaseResult.Error("ERROR", "Failed")
            coEvery { getPreferredExerciseUseCase() } returns BaseResult.Success(emptyList())
            coEvery { getPreferredExerciseTypesUseCase() } returns BaseResult.Success(emptyList<PreferredExercise>())
            coEvery { getMemberIdUseCase() } returns "member1"

            // When
            viewModel =
                FeedListViewModel(
                    getFeedsUseCase,
                    getPreferredExerciseUseCase,
                    getPreferredExerciseTypesUseCase,
                    getMemberIdUseCase,
                    deleteFeedUseCase,
                )

            viewModel.toastEvent.test {
                testDispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isNotNull()
            }
        }

    @Test
    fun `deleteFeed - 삭제 성공 시 목록에서 제거된다`() =
        runTest {
            // Given
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            coEvery { deleteFeedUseCase(1L) } returns BaseResult.Success(Unit)

            // When
            viewModel.deleteFeed(1L)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.feedList.value).isEmpty()
            coVerify { deleteFeedUseCase(1L) }
        }

    @Test
    fun `deleteFeed - 삭제 실패 시 토스트 이벤트를 발생시킨다`() =
        runTest {
            // Given
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            coEvery { deleteFeedUseCase(1L) } returns BaseResult.Error("ERROR", "Delete failed")

            // When & Then
            viewModel.toastEvent.test {
                viewModel.deleteFeed(1L)
                testDispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isNotNull()
            }
        }

    @Test
    fun `requestShowCategoryBottomSheet - 운동 타입 목록이 있으면 바텀시트를 표시한다`() =
        runTest {
            // Given
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            viewModel.requestShowCategoryBottomSheet()

            // Then
            assertThat(viewModel.showCategoryBottomSheet.value).isNotNull()
        }

    @Test
    fun `onCategoryBottomSheetShown - 바텀시트 표시 후 상태를 초기화한다`() =
        runTest {
            // Given
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.requestShowCategoryBottomSheet()

            // When
            viewModel.onCategoryBottomSheetShown()

            // Then
            assertThat(viewModel.showCategoryBottomSheet.value).isNull()
        }
}
