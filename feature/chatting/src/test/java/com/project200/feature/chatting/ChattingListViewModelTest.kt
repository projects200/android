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
            chatRoomId = 1L,
            opponentMemberId = "opponent1",
            opponentNickname = "상대방",
            opponentProfileUrl = null,
            latestMessage = "안녕하세요",
            latestMessageTime = LocalDateTime.of(2025, 1, 1, 10, 0),
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
                assertThat(rooms[0].chatRoomId).isEqualTo(1L)
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
}
