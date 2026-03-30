package com.project200.undabang.profile.mypage

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ProfileImage
import com.project200.domain.model.ProfileImageList
import com.project200.domain.usecase.ChangeThumbnailUseCase
import com.project200.domain.usecase.DeleteProfileImageUseCase
import com.project200.domain.usecase.GetProfileImagesUseCase
import com.project200.undabang.profile.utils.ProfileImageErrorType
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
class ProfileImageDetailViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockGetProfileImagesUseCase: GetProfileImagesUseCase

    @MockK
    private lateinit var mockDeleteProfileImageUseCase: DeleteProfileImageUseCase

    @MockK
    private lateinit var mockChangeThumbnailUseCase: ChangeThumbnailUseCase

    @MockK(relaxed = true)
    private lateinit var mockContext: Context

    private lateinit var viewModel: ProfileImageDetailViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val sampleThumbnail = ProfileImage(id = 1L, url = "https://example.com/thumb.jpg")
    private val sampleImages =
        listOf(
            ProfileImage(id = 2L, url = "https://example.com/image2.jpg"),
            ProfileImage(id = 3L, url = "https://example.com/image3.jpg"),
        )
    private val sampleImageList =
        ProfileImageList(
            thumbnail = sampleThumbnail,
            images = sampleImages,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel =
            ProfileImageDetailViewModel(
                getProfileImagesUseCase = mockGetProfileImagesUseCase,
                deleteProfileImageUseCase = mockDeleteProfileImageUseCase,
                context = mockContext,
                changeThumbnailUseCase = mockChangeThumbnailUseCase,
            )
    }

    @Test
    fun `init - ViewModel 초기화 시 getProfileImageList가 호출된다`() =
        runTest {
            // Given
            coEvery { mockGetProfileImagesUseCase() } returns BaseResult.Success(sampleImageList)

            // When
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { mockGetProfileImagesUseCase() }
        }

    @Test
    fun `getProfileImageList - 성공 시 썸네일이 맨 앞에 위치한 리스트가 반환된다`() =
        runTest {
            // Given
            coEvery { mockGetProfileImagesUseCase() } returns BaseResult.Success(sampleImageList)

            // When
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val result = viewModel.profileImages.value
            assertThat(result).isNotNull()
            assertThat(result!!.first()).isEqualTo(sampleThumbnail)
            assertThat(result).hasSize(3)
        }

    @Test
    fun `getProfileImageList - 썸네일 없이 이미지만 있는 경우 이미지 리스트가 반환된다`() =
        runTest {
            // Given
            val imageListNoThumbnail = ProfileImageList(thumbnail = null, images = sampleImages)
            coEvery { mockGetProfileImagesUseCase() } returns BaseResult.Success(imageListNoThumbnail)

            // When
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val result = viewModel.profileImages.value
            assertThat(result).isNotNull()
            assertThat(result).hasSize(2)
            assertThat(result).isEqualTo(sampleImages)
        }

    @Test
    fun `getProfileImageList - 이미지가 없는 경우 더미 이미지가 추가된다`() =
        runTest {
            // Given
            val emptyImageList = ProfileImageList(thumbnail = null, images = emptyList())
            coEvery { mockGetProfileImagesUseCase() } returns BaseResult.Success(emptyImageList)

            // When
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val result = viewModel.profileImages.value
            assertThat(result).isNotNull()
            assertThat(result).hasSize(1)
            assertThat(result!!.first().id).isEqualTo(ProfileImageDetailViewModel.EMPTY_ID)
        }

    @Test
    fun `getProfileImageList - 실패 시 LOAD_FAILED 에러가 emit된다`() =
        runTest {
            // Given
            coEvery { mockGetProfileImagesUseCase() } returns BaseResult.Error("ERROR", "로드 실패")

            // When
            createViewModel()

            viewModel.getProfileImageErrorToast.test {
                testDispatcher.scheduler.advanceUntilIdle()

                // Then
                assertThat(awaitItem()).isEqualTo(ProfileImageErrorType.LOAD_FAILED)
            }
        }

    @Test
    fun `deleteImage - 성공 시 이미지가 리스트에서 제거된다`() =
        runTest {
            // Given
            coEvery { mockGetProfileImagesUseCase() } returns BaseResult.Success(sampleImageList)
            coEvery { mockDeleteProfileImageUseCase(any()) } returns BaseResult.Success(Unit)
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val imageToDelete = 2L

            // When
            viewModel.deleteImage(imageToDelete)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val result = viewModel.profileImages.value
            assertThat(result).isNotNull()
            assertThat(result!!.none { it.id == imageToDelete }).isTrue()
        }

    @Test
    fun `deleteImage - 성공 후 리스트가 비면 더미 이미지가 추가된다`() =
        runTest {
            // Given
            val singleImageList =
                ProfileImageList(
                    thumbnail = sampleThumbnail,
                    images = emptyList(),
                )
            coEvery { mockGetProfileImagesUseCase() } returns BaseResult.Success(singleImageList)
            coEvery { mockDeleteProfileImageUseCase(any()) } returns BaseResult.Success(Unit)
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When - 유일한 이미지(썸네일) 삭제
            viewModel.deleteImage(sampleThumbnail.id)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val result = viewModel.profileImages.value
            assertThat(result).isNotNull()
            assertThat(result).hasSize(1)
            assertThat(result!!.first().id).isEqualTo(ProfileImageDetailViewModel.EMPTY_ID)
        }

    @Test
    fun `deleteImage - 성공 시 imageDeleteResult에 Success가 emit된다`() =
        runTest {
            // Given
            coEvery { mockGetProfileImagesUseCase() } returns BaseResult.Success(sampleImageList)
            coEvery { mockDeleteProfileImageUseCase(any()) } returns BaseResult.Success(Unit)
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When & Then
            viewModel.imageDeleteResult.test {
                viewModel.deleteImage(2L)
                testDispatcher.scheduler.advanceUntilIdle()

                val result = awaitItem()
                assertThat(result).isInstanceOf(BaseResult.Success::class.java)
            }
        }

    @Test
    fun `deleteImage - 실패 시 imageDeleteResult에 Error가 emit된다`() =
        runTest {
            // Given
            coEvery { mockGetProfileImagesUseCase() } returns BaseResult.Success(sampleImageList)
            coEvery { mockDeleteProfileImageUseCase(any()) } returns BaseResult.Error("ERROR", "삭제 실패")
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When & Then
            viewModel.imageDeleteResult.test {
                viewModel.deleteImage(2L)
                testDispatcher.scheduler.advanceUntilIdle()

                val result = awaitItem()
                assertThat(result).isInstanceOf(BaseResult.Error::class.java)
            }
        }

    @Test
    fun `changeThumbnail - 성공 시 새 썸네일이 리스트 맨 앞으로 이동한다`() =
        runTest {
            // Given
            coEvery { mockGetProfileImagesUseCase() } returns BaseResult.Success(sampleImageList)
            coEvery { mockChangeThumbnailUseCase(any()) } returns BaseResult.Success(Unit)
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val newThumbnailId = 2L

            // When
            viewModel.changeThumbnail(newThumbnailId)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val result = viewModel.profileImages.value
            assertThat(result).isNotNull()
            assertThat(result!!.first().id).isEqualTo(newThumbnailId)
        }

    @Test
    fun `changeThumbnail - 성공 시 changeThumbnailResult에 Success가 emit된다`() =
        runTest {
            // Given
            coEvery { mockGetProfileImagesUseCase() } returns BaseResult.Success(sampleImageList)
            coEvery { mockChangeThumbnailUseCase(any()) } returns BaseResult.Success(Unit)
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When & Then
            viewModel.changeThumbnailResult.test {
                viewModel.changeThumbnail(2L)
                testDispatcher.scheduler.advanceUntilIdle()

                val result = awaitItem()
                assertThat(result).isInstanceOf(BaseResult.Success::class.java)
            }
        }

    @Test
    fun `changeThumbnail - 실패 시 리스트가 변경되지 않고 Error가 emit된다`() =
        runTest {
            // Given
            coEvery { mockGetProfileImagesUseCase() } returns BaseResult.Success(sampleImageList)
            coEvery { mockChangeThumbnailUseCase(any()) } returns BaseResult.Error("ERROR", "변경 실패")
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val originalList = viewModel.profileImages.value

            // When & Then
            viewModel.changeThumbnailResult.test {
                viewModel.changeThumbnail(2L)
                testDispatcher.scheduler.advanceUntilIdle()

                val result = awaitItem()
                assertThat(result).isInstanceOf(BaseResult.Error::class.java)
            }

            // 리스트가 변경되지 않음
            assertThat(viewModel.profileImages.value).isEqualTo(originalList)
        }

    @Test
    fun `getProfileImageList - 수동 호출 시 이미지 리스트가 다시 로드된다`() =
        runTest {
            // Given
            coEvery { mockGetProfileImagesUseCase() } returns BaseResult.Success(sampleImageList)
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val newImageList =
                ProfileImageList(
                    thumbnail = ProfileImage(id = 10L, url = "https://new.com/thumb.jpg"),
                    images = emptyList(),
                )
            coEvery { mockGetProfileImagesUseCase() } returns BaseResult.Success(newImageList)

            // When
            viewModel.getProfileImageList()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val result = viewModel.profileImages.value
            assertThat(result).isNotNull()
            assertThat(result!!.first().id).isEqualTo(10L)
            coVerify(exactly = 2) { mockGetProfileImagesUseCase() }
        }
}
