package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CreateFeedModel
import com.project200.domain.model.FeedCreateResult
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
class CreateFeedUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: FeedRepository

    private lateinit var useCase: CreateFeedUseCase

    private val sampleFeedModel = CreateFeedModel(
        feedContent = "테스트 피드 내용",
        feedTypeId = 1L
    )

    @Before
    fun setUp() {
        useCase = CreateFeedUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 repository createFeed 호출 및 성공 결과 반환`() = runTest {
        // Given
        val feedId = 123L
        val successResult = BaseResult.Success(FeedCreateResult(feedId))
        coEvery { mockRepository.createFeed(sampleFeedModel) } returns successResult

        // When
        val result = useCase(sampleFeedModel)

        // Then
        coVerify(exactly = 1) { mockRepository.createFeed(sampleFeedModel) }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data.feedId).isEqualTo(feedId)
    }

    @Test
    fun `invoke 호출 시 repository createFeed 호출 및 실패 결과 반환`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR", "Feed creation failed")
        coEvery { mockRepository.createFeed(sampleFeedModel) } returns errorResult

        // When
        val result = useCase(sampleFeedModel)

        // Then
        coVerify(exactly = 1) { mockRepository.createFeed(sampleFeedModel) }
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `feedTypeId가 null인 피드 생성`() = runTest {
        // Given
        val feedModelWithoutType = CreateFeedModel(
            feedContent = "타입 없는 피드",
            feedTypeId = null
        )
        val successResult = BaseResult.Success(FeedCreateResult(1L))
        coEvery { mockRepository.createFeed(feedModelWithoutType) } returns successResult

        // When
        val result = useCase(feedModelWithoutType)

        // Then
        coVerify(exactly = 1) { mockRepository.createFeed(feedModelWithoutType) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }
}
