package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.FeedPicture
import com.project200.domain.repository.FeedRepository
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
class UploadFeedImagesUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: FeedRepository

    private lateinit var useCase: UploadFeedImagesUseCase

    @Before
    fun setUp() {
        useCase = UploadFeedImagesUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 이미지 업로드 성공`() = runTest {
        // Given
        val feedId = 1L
        val imageUris = listOf("content://image1.jpg", "content://image2.jpg")
        val uploadedPictures = listOf(
            FeedPicture(feedPictureId = 1L, feedPictureUrl = "https://cdn.example.com/1.jpg"),
            FeedPicture(feedPictureId = 2L, feedPictureUrl = "https://cdn.example.com/2.jpg")
        )
        val successResult = BaseResult.Success(uploadedPictures)
        coEvery { mockRepository.uploadFeedImages(feedId, imageUris) } returns successResult

        // When
        val result = useCase(feedId, imageUris)

        // Then
        coVerify(exactly = 1) { mockRepository.uploadFeedImages(feedId, imageUris) }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data).hasSize(2)
    }

    @Test
    fun `invoke 호출 시 이미지 업로드 실패`() = runTest {
        // Given
        val feedId = 1L
        val imageUris = listOf("content://image1.jpg")
        val errorResult = BaseResult.Error("ERR", "Upload failed")
        coEvery { mockRepository.uploadFeedImages(feedId, imageUris) } returns errorResult

        // When
        val result = useCase(feedId, imageUris)

        // Then
        coVerify(exactly = 1) { mockRepository.uploadFeedImages(feedId, imageUris) }
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `빈 이미지 목록 업로드`() = runTest {
        // Given
        val feedId = 1L
        val imageUris = emptyList<String>()
        val successResult = BaseResult.Success(emptyList<FeedPicture>())
        coEvery { mockRepository.uploadFeedImages(feedId, imageUris) } returns successResult

        // When
        val result = useCase(feedId, imageUris)

        // Then
        coVerify(exactly = 1) { mockRepository.uploadFeedImages(feedId, imageUris) }
        assertThat((result as BaseResult.Success).data).isEmpty()
    }
}
