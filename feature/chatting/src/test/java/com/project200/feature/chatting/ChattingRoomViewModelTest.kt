package com.project200.feature.chatting

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ChattingMessage
import com.project200.domain.model.ChattingMessageList
import com.project200.domain.model.OpponentStatus
import com.project200.domain.usecase.ConnectChatRoomUseCase
import com.project200.domain.usecase.DisconnectChatRoomUseCase
import com.project200.domain.usecase.ExitChatRoomUseCase
import com.project200.domain.usecase.GetChatMessagesUseCase
import com.project200.domain.usecase.GetNewChatMessagesUseCase
import com.project200.domain.usecase.ObserveOpponentStatusUseCase
import com.project200.domain.usecase.ObserveSocketMessagesUseCase
import com.project200.domain.usecase.SendSocketMessageUseCase
import com.project200.feature.chatting.chattingRoom.ChattingRoomViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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
class ChattingRoomViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var getChatMessagesUseCase: GetChatMessagesUseCase

    @MockK
    private lateinit var getNewChatMessagesUseCase: GetNewChatMessagesUseCase

    @MockK
    private lateinit var exitChatRoomUseCase: ExitChatRoomUseCase

    @MockK
    private lateinit var connectChatRoomUseCase: ConnectChatRoomUseCase

    @MockK
    private lateinit var disconnectChatRoomUseCase: DisconnectChatRoomUseCase

    @MockK
    private lateinit var observeSocketMessagesUseCase: ObserveSocketMessagesUseCase

    @MockK
    private lateinit var observeOpponentStatusUseCase: ObserveOpponentStatusUseCase

    @MockK
    private lateinit var sendSocketMessageUseCase: SendSocketMessageUseCase

    private lateinit var viewModel: ChattingRoomViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val sampleMessage = ChattingMessage(
        chatId = 1L,
        content = "안녕하세요",
        sentAt = LocalDateTime.of(2025, 1, 1, 10, 0),
        isMine = false,
        chatType = "NORMAL",
        senderNickname = "상대방",
        senderProfileUrl = null,
        showProfile = true,
        showTime = true
    )

    private val sampleMessageList = ChattingMessageList(
        messages = listOf(sampleMessage),
        hasNext = false
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { observeSocketMessagesUseCase() } returns emptyFlow()
        every { observeOpponentStatusUseCase() } returns emptyFlow()
        viewModel = ChattingRoomViewModel(
            getChatMessagesUseCase,
            getNewChatMessagesUseCase,
            exitChatRoomUseCase,
            connectChatRoomUseCase,
            disconnectChatRoomUseCase,
            observeSocketMessagesUseCase,
            observeOpponentStatusUseCase,
            sendSocketMessageUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setId - 초기 메시지를 로드한다`() = runTest {
        // Given
        coEvery { getChatMessagesUseCase(1L, null, 30) } returns BaseResult.Success(sampleMessageList)

        // When
        viewModel.setId(1L, "opponent1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.messages.test {
            assertThat(awaitItem()).hasSize(1)
        }
        coVerify { getChatMessagesUseCase(1L, null, 30) }
    }

    @Test
    fun `setId - 메시지 로드 실패 시 토스트 이벤트 발생`() = runTest {
        // Given
        coEvery { getChatMessagesUseCase(1L, null, 30) } returns BaseResult.Error("ERROR", "Failed")

        // When & Then
        viewModel.toast.test {
            viewModel.setId(1L, "opponent1")
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isNotNull()
        }
    }

    @Test
    fun `sendMessage - 빈 메시지는 전송하지 않는다`() = runTest {
        // Given
        coEvery { getChatMessagesUseCase(1L, null, 30) } returns BaseResult.Success(sampleMessageList)
        viewModel.setId(1L, "opponent1")
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.sendMessage("")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 0) { sendSocketMessageUseCase(any()) }
    }

    @Test
    fun `sendMessage - 메시지를 전송한다`() = runTest {
        // Given
        coEvery { getChatMessagesUseCase(1L, null, 30) } returns BaseResult.Success(sampleMessageList)
        coEvery { sendSocketMessageUseCase("테스트") } just runs
        viewModel.setId(1L, "opponent1")
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.sendMessage("테스트")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { sendSocketMessageUseCase("테스트") }
    }

    @Test
    fun `exitChatRoom - 채팅방 나가기를 호출한다`() = runTest {
        // Given
        coEvery { getChatMessagesUseCase(1L, null, 30) } returns BaseResult.Success(sampleMessageList)
        coEvery { exitChatRoomUseCase(1L) } returns BaseResult.Success(Unit)
        viewModel.setId(1L, "opponent1")
        testDispatcher.scheduler.advanceUntilIdle()

        // When & Then
        viewModel.exitResult.test {
            viewModel.exitChatRoom()
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isInstanceOf(BaseResult.Success::class.java)
        }
    }

    @Test
    fun `connectAndSync - 소켓 연결 및 누락 메시지를 동기화한다`() = runTest {
        // Given
        coEvery { getChatMessagesUseCase(1L, null, 30) } returns BaseResult.Success(sampleMessageList)
        every { connectChatRoomUseCase(1L) } just runs
        coEvery { getNewChatMessagesUseCase(1L, 1L) } returns BaseResult.Success(
            ChattingMessageList(emptyList(), false)
        )
        viewModel.setId(1L, "opponent1")
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.connectAndSync()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { connectChatRoomUseCase(1L) }
    }

    @Test
    fun `disconnect - 소켓 연결을 해제한다`() = runTest {
        // Given
        coEvery { getChatMessagesUseCase(1L, null, 30) } returns BaseResult.Success(sampleMessageList)
        every { disconnectChatRoomUseCase() } just runs
        viewModel.setId(1L, "opponent1")
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.disconnect()

        // Then
        coVerify { disconnectChatRoomUseCase() }
    }

    @Test
    fun `loadPreviousMessages - 이전 메시지가 없으면 조회하지 않는다`() = runTest {
        // Given
        coEvery { getChatMessagesUseCase(1L, null, 30) } returns BaseResult.Success(sampleMessageList)
        viewModel.setId(1L, "opponent1")
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.loadPreviousMessages()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { getChatMessagesUseCase(any(), any(), any()) }
    }
}
