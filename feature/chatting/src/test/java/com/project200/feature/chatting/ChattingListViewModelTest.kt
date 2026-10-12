package com.project200.feature.chatting

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.common.utils.ClockProvider
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ChattingRoom
import com.project200.domain.usecase.GetChattingRoomsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.CompletableDeferred
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
class ChattingListViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var getChattingRoomsUseCase: GetChattingRoomsUseCase

    @MockK
    private lateinit var clockProvider: ClockProvider

    private lateinit var viewModel: ChattingListViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val sampleChattingRoom =
        ChattingRoom(
            id = 1L,
            opponentMemberId = "opponent1",
            nickname = "상대방",
            profileImageUrl = null,
            thumbnailImageUrl = null,
            lastMessage = "안녕하세요",
            lastChattedAt = LocalDateTime.of(2025, 1, 1, 10, 0),
            unreadCount = 3,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ChattingListViewModel(getChattingRoomsUseCase, clockProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fetchChattingRooms - 성공 시 채팅방 목록을 업데이트한다`() =
        runTest {
            // Given
            coEvery { getChattingRoomsUseCase() } returns BaseResult.Success(listOf(sampleChattingRoom))

            // When
            viewModel.fetchChattingRooms()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            viewModel.chattingRooms.test {
                val rooms = awaitItem()
                assertThat(rooms).hasSize(1)
                assertThat(rooms[0].id).isEqualTo(1L)
            }
            coVerify { getChattingRoomsUseCase() }
        }

    @Test
    fun `fetchChattingRooms - 빈 목록도 정상 처리한다`() =
        runTest {
            // Given
            coEvery { getChattingRoomsUseCase() } returns BaseResult.Success(emptyList())

            // When
            viewModel.fetchChattingRooms()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            viewModel.chattingRooms.test {
                assertThat(awaitItem()).isEmpty()
            }
        }

    @Test
    fun `fetchChattingRooms - 에러 발생 시 기존 목록 유지`() =
        runTest {
            // Given
            coEvery { getChattingRoomsUseCase() } returns BaseResult.Success(listOf(sampleChattingRoom))
            viewModel.fetchChattingRooms()
            testDispatcher.scheduler.advanceUntilIdle()

            coEvery { getChattingRoomsUseCase() } returns BaseResult.Error("ERROR", "Failed")

            // When
            viewModel.fetchChattingRooms()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            viewModel.chattingRooms.test {
                assertThat(awaitItem()).hasSize(1)
            }
        }

    @Test
    fun `빈 성공 뒤 조회 중이거나 실패하면 완료 상태를 유지하지 않는다`() =
        runTest {
            assertThat(viewModel.hasLoadedChattingRooms.value).isFalse()
            coEvery { getChattingRoomsUseCase() } returns BaseResult.Success(emptyList())
            viewModel.fetchChattingRooms()
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(viewModel.hasLoadedChattingRooms.value).isTrue()

            coEvery { getChattingRoomsUseCase() } returns BaseResult.Error("ERROR", "Failed")
            viewModel.fetchChattingRooms()
            assertThat(viewModel.hasLoadedChattingRooms.value).isFalse()
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.chattingRooms.value).isEmpty()
            assertThat(viewModel.hasLoadedChattingRooms.value).isFalse()
        }

    @Test
    fun `이전 빈 응답은 최신 채팅 조회의 완료 상태가 되지 않는다`() =
        runTest {
            val previousResponse = CompletableDeferred<BaseResult<List<ChattingRoom>>>()
            val latestResponse = CompletableDeferred<BaseResult<List<ChattingRoom>>>()
            var requestCount = 0
            coEvery { getChattingRoomsUseCase() } coAnswers {
                if (++requestCount == 1) previousResponse.await() else latestResponse.await()
            }

            viewModel.fetchChattingRooms()
            testDispatcher.scheduler.runCurrent()
            viewModel.fetchChattingRooms()
            testDispatcher.scheduler.runCurrent()
            latestResponse.complete(BaseResult.Success(emptyList()))
            testDispatcher.scheduler.runCurrent()
            assertThat(viewModel.hasLoadedChattingRooms.value).isTrue()

            previousResponse.complete(BaseResult.Success(emptyList()))
            testDispatcher.scheduler.runCurrent()
            assertThat(viewModel.hasLoadedChattingRooms.value).isFalse()
        }
}
