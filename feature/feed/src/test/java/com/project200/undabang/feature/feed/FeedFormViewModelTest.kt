package com.project200.undabang.feature.feed

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CreateFeedResult
import com.project200.domain.model.Feed
import com.project200.domain.model.PreferredExercise
import com.project200.domain.model.UserProfile
import com.project200.domain.usecase.CreateFeedUseCase
import com.project200.domain.usecase.DeleteFeedImageUseCase
import com.project200.domain.usecase.GetFeedDetailUseCase
import com.project200.domain.usecase.GetPreferredExerciseTypesUseCase
import com.project200.domain.usecase.GetPreferredExerciseUseCase
import com.project200.domain.usecase.GetUserProfileUseCase
import com.project200.domain.usecase.UpdateFeedUseCase
import com.project200.domain.usecase.UploadFeedImagesUseCase
import com.project200.undabang.feature.feed.form.FeedFormViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
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
class FeedFormViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var getUserProfileUseCase: GetUserProfileUseCase

    @MockK
    private lateinit var getPreferredExerciseUseCase: GetPreferredExerciseUseCase

    @MockK
    private lateinit var getPreferredExerciseTypesUseCase: GetPreferredExerciseTypesUseCase

    @MockK
    private lateinit var getFeedDetailUseCase: GetFeedDetailUseCase

    @MockK
    private lateinit var createFeedUseCase: CreateFeedUseCase

    @MockK
    private lateinit var updateFeedUseCase: UpdateFeedUseCase

    @MockK
    private lateinit var uploadFeedImagesUseCase: UploadFeedImagesUseCase

    @MockK
    private lateinit var deleteFeedImageUseCase: DeleteFeedImageUseCase

    private lateinit var viewModel: FeedFormViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val sampleProfile = UserProfile(
        memberId = "member1",
        nickname = "테스터",
        gender = "MALE",
        birthDate = "1990-01-01",
        memberScore = 80,
        thumbnailUrl = null
    )

    private val sampleExercise = PreferredExercise(
        preferredExerciseId = 1L,
        exerciseTypeId = 1L,
        name = "헬스",
        skillLevel = "BEGINNER",
        daysOfWeek = List(7) { false },
        imageUrl = null
    )

    private val sampleFeed = Feed(
        feedId = 1L,
        memberId = "member1",
        nickname = "테스터",
        feedTypeName = "헬스",
        feedTypeId = 1L,
        feedContent = "기존 내용",
        feedPictures = emptyList(),
        feedLikesCount = 0,
        feedCommentsCount = 0,
        feedIsLiked = false,
        createdAt = "2025-01-01T10:00:00",
        thumbnailUrl = null
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = FeedFormViewModel(
            getUserProfileUseCase,
            getPreferredExerciseUseCase,
            getPreferredExerciseTypesUseCase,
            getFeedDetailUseCase,
            createFeedUseCase,
            updateFeedUseCase,
            uploadFeedImagesUseCase,
            deleteFeedImageUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initData - 생성 모드로 초기화하면 isEditMode가 false`() = runTest {
        // Given
        coEvery { getUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
        coEvery { getPreferredExerciseUseCase() } returns BaseResult.Success(listOf(sampleExercise))
        coEvery { getPreferredExerciseTypesUseCase() } returns BaseResult.Success(emptyList())

        // When
        viewModel.initData()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.isEditMode.value).isFalse()
    }

    @Test
    fun `initData - 수정 모드로 초기화하면 isEditMode가 true`() = runTest {
        // Given
        coEvery { getUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
        coEvery { getPreferredExerciseUseCase() } returns BaseResult.Success(listOf(sampleExercise))
        coEvery { getPreferredExerciseTypesUseCase() } returns BaseResult.Success(emptyList())
        coEvery { getFeedDetailUseCase(1L) } returns BaseResult.Success(sampleFeed)

        // When
        viewModel.initData(feedId = 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.isEditMode.value).isTrue()
    }

    @Test
    fun `initData - 사용자 프로필을 로드한다`() = runTest {
        // Given
        coEvery { getUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
        coEvery { getPreferredExerciseUseCase() } returns BaseResult.Success(emptyList())
        coEvery { getPreferredExerciseTypesUseCase() } returns BaseResult.Success(emptyList())

        // When
        viewModel.initData()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.userProfile.value).isEqualTo(sampleProfile)
    }

    @Test
    fun `initData - 운동 타입을 로드한다`() = runTest {
        // Given
        coEvery { getUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
        coEvery { getPreferredExerciseUseCase() } returns BaseResult.Success(listOf(sampleExercise))
        coEvery { getPreferredExerciseTypesUseCase() } returns BaseResult.Success(emptyList())

        // When
        viewModel.initData()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.exerciseTypes.value).hasSize(1)
    }

    @Test
    fun `selectType - 운동 타입을 선택한다`() = runTest {
        // Given
        coEvery { getUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
        coEvery { getPreferredExerciseUseCase() } returns BaseResult.Success(listOf(sampleExercise))
        coEvery { getPreferredExerciseTypesUseCase() } returns BaseResult.Success(emptyList())
        viewModel.initData()
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.selectType(sampleExercise)

        // Then
        assertThat(viewModel.selectedType.value).isEqualTo(sampleExercise)
    }

    @Test
    fun `addImages - 이미지를 추가한다`() {
        // Given
        val uri = mockk<Uri>()

        // When
        viewModel.addImages(listOf(uri))

        // Then
        assertThat(viewModel.selectedImages.value).hasSize(1)
    }

    @Test
    fun `removeImage - 이미지를 제거한다`() {
        // Given
        val uri = mockk<Uri>()
        viewModel.addImages(listOf(uri))

        // When
        viewModel.removeImage(uri)

        // Then
        assertThat(viewModel.selectedImages.value).isEmpty()
    }

    @Test
    fun `submitFeed - 빈 내용은 토스트를 표시한다`() = runTest {
        // When & Then
        viewModel.toastEvent.test {
            viewModel.submitFeed("")
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isNotNull()
        }
    }

    @Test
    fun `submitFeed - 생성 모드에서 피드 생성 성공`() = runTest {
        // Given
        coEvery { getUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
        coEvery { getPreferredExerciseUseCase() } returns BaseResult.Success(listOf(sampleExercise))
        coEvery { getPreferredExerciseTypesUseCase() } returns BaseResult.Success(emptyList())
        coEvery { createFeedUseCase(any()) } returns BaseResult.Success(CreateFeedResult(feedId = 1L))
        viewModel.initData()
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.submitFeed("테스트 내용")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.createSuccess.value).isEqualTo(1L)
        coVerify { createFeedUseCase(any()) }
    }

    @Test
    fun `submitFeed - 생성 모드에서 이미지와 함께 피드 생성`() = runTest {
        // Given
        coEvery { getUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
        coEvery { getPreferredExerciseUseCase() } returns BaseResult.Success(emptyList())
        coEvery { getPreferredExerciseTypesUseCase() } returns BaseResult.Success(emptyList())
        coEvery { createFeedUseCase(any()) } returns BaseResult.Success(CreateFeedResult(feedId = 1L))
        coEvery { uploadFeedImagesUseCase(1L, any()) } returns BaseResult.Success(Unit)

        val mockUri = mockk<Uri>()
        every { mockUri.toString() } returns "content://image/1"
        viewModel.initData()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.addImages(listOf(mockUri))

        // When
        viewModel.submitFeed("테스트 내용")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { uploadFeedImagesUseCase(1L, any()) }
    }

    @Test
    fun `submitFeed - 수정 모드에서 피드 업데이트 성공`() = runTest {
        // Given
        coEvery { getUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
        coEvery { getPreferredExerciseUseCase() } returns BaseResult.Success(listOf(sampleExercise))
        coEvery { getPreferredExerciseTypesUseCase() } returns BaseResult.Success(emptyList())
        coEvery { getFeedDetailUseCase(1L) } returns BaseResult.Success(sampleFeed)
        coEvery { updateFeedUseCase(any()) } returns BaseResult.Success(Unit)
        viewModel.initData(feedId = 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.submitFeed("수정된 내용")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.updateSuccess.value).isTrue()
        coVerify { updateFeedUseCase(any()) }
    }

    @Test
    fun `submitFeed - 생성 실패 시 토스트 이벤트 발생`() = runTest {
        // Given
        coEvery { getUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
        coEvery { getPreferredExerciseUseCase() } returns BaseResult.Success(emptyList())
        coEvery { getPreferredExerciseTypesUseCase() } returns BaseResult.Success(emptyList())
        coEvery { createFeedUseCase(any()) } returns BaseResult.Error("ERROR", "Failed")
        viewModel.initData()
        testDispatcher.scheduler.advanceUntilIdle()

        // When & Then
        viewModel.toastEvent.test {
            viewModel.submitFeed("테스트 내용")
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isNotNull()
        }
    }

    @Test
    fun `requestShowDabangSelection - 운동 타입이 있으면 바텀시트를 표시한다`() = runTest {
        // Given
        coEvery { getUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
        coEvery { getPreferredExerciseUseCase() } returns BaseResult.Success(listOf(sampleExercise))
        coEvery { getPreferredExerciseTypesUseCase() } returns BaseResult.Success(emptyList())
        viewModel.initData()
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.requestShowDabangSelection()

        // Then
        assertThat(viewModel.showDabangSelection.value).isNotNull()
    }

    @Test
    fun `onDabangSelectionShown - 바텀시트 상태를 초기화한다`() = runTest {
        // Given
        coEvery { getUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
        coEvery { getPreferredExerciseUseCase() } returns BaseResult.Success(listOf(sampleExercise))
        coEvery { getPreferredExerciseTypesUseCase() } returns BaseResult.Success(emptyList())
        viewModel.initData()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.requestShowDabangSelection()

        // When
        viewModel.onDabangSelectionShown()

        // Then
        assertThat(viewModel.showDabangSelection.value).isNull()
    }
}
