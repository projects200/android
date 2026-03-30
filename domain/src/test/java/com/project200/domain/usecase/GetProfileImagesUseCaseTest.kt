package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ProfileImage
import com.project200.domain.model.ProfileImageList
import com.project200.domain.repository.MemberRepository
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
class GetProfileImagesUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MemberRepository

    private lateinit var useCase: GetProfileImagesUseCase

    private val sampleThumbnail = ProfileImage(id = 1L, url = "https://example.com/thumb.jpg")
    private val sampleImages = listOf(
        ProfileImage(id = 2L, url = "https://example.com/image1.jpg"),
        ProfileImage(id = 3L, url = "https://example.com/image2.jpg")
    )
    private val sampleProfileImageList = ProfileImageList(
        thumbnail = sampleThumbnail,
        images = sampleImages
    )

    @Before
    fun setUp() {
        useCase = GetProfileImagesUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 프로필 이미지 목록 성공적으로 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(sampleProfileImageList)
        coEvery { mockRepository.getProfileImages() } returns successResult

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.getProfileImages() }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data.images).hasSize(2)
    }

    @Test
    fun `썸네일이 없는 경우`() = runTest {
        // Given
        val imageListWithoutThumbnail = ProfileImageList(thumbnail = null, images = sampleImages)
        coEvery { mockRepository.getProfileImages() } returns BaseResult.Success(imageListWithoutThumbnail)

        // When
        val result = useCase()

        // Then
        assertThat((result as BaseResult.Success).data.thumbnail).isNull()
    }

    @Test
    fun `이미지가 없는 경우`() = runTest {
        // Given
        val emptyImageList = ProfileImageList(thumbnail = null, images = emptyList())
        coEvery { mockRepository.getProfileImages() } returns BaseResult.Success(emptyImageList)

        // When
        val result = useCase()

        // Then
        val data = (result as BaseResult.Success).data
        assertThat(data.thumbnail).isNull()
        assertThat(data.images).isEmpty()
    }

    @Test
    fun `프로필 이미지 조회 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR", "Failed to fetch profile images")
        coEvery { mockRepository.getProfileImages() } returns errorResult

        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `썸네일만 있는 경우`() = runTest {
        // Given
        val thumbnailOnly = ProfileImageList(thumbnail = sampleThumbnail, images = emptyList())
        coEvery { mockRepository.getProfileImages() } returns BaseResult.Success(thumbnailOnly)

        // When
        val result = useCase()

        // Then
        val data = (result as BaseResult.Success).data
        assertThat(data.thumbnail).isNotNull()
        assertThat(data.images).isEmpty()
    }
}
