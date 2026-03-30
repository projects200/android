package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.UpdateFeedModel
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
class UpdateFeedUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: FeedRepository

    private lateinit var useCase: UpdateFeedUseCase

    private val sampleUpdateModel = UpdateFeedModel(
        feedId = 1L,
        feedContent = "수정된 피드 내용",
        feedTypeId = 2L
    )

    @Before
    fun setUp() {
        useCase = UpdateFeedUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 피드 업데이트 성공`() = runTest {
        // Given
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.updateFeed(sampleUpdateModel) } returns successResult

        // When
        val result = useCase(sampleUpdateModel)

        // Then
        coVerify(exactly = 1) { mockRepository.updateFeed(sampleUpdateModel) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `invoke 호출 시 피드 업데이트 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR", "Update failed")
        coEvery { mockRepository.updateFeed(sampleUpdateModel) } returns errorResult

        // When
        val result = useCase(sampleUpdateModel)

        // Then
        coVerify(exactly = 1) { mockRepository.updateFeed(sampleUpdateModel) }
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `feedTypeId null로 업데이트`() = runTest {
        // Given
        val updateModelWithoutType = UpdateFeedModel(
            feedId = 1L,
            feedContent = "타입 없이 수정",
            feedTypeId = null
        )
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.updateFeed(updateModelWithoutType) } returns successResult

        // When
        val result = useCase(updateModelWithoutType)

        // Then
        coVerify(exactly = 1) { mockRepository.updateFeed(updateModelWithoutType) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }
}
