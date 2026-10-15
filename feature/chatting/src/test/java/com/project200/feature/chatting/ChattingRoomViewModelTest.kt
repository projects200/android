package com.project200.feature.chatting

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ChatSocketConnection
import com.project200.domain.model.ChatSocketStatus
import com.project200.domain.model.ChattingMessage
import com.project200.domain.model.ChattingModel
import com.project200.domain.model.OpponentStatus
import com.project200.domain.usecase.ConnectChatRoomUseCase
import com.project200.domain.usecase.DisconnectChatRoomUseCase
import com.project200.domain.usecase.ExitChatRoomUseCase
import com.project200.domain.usecase.GetChatMessagesUseCase
import com.project200.domain.usecase.GetNewChatMessagesUseCase
import com.project200.domain.usecase.ObserveChatConnectionUseCase
import com.project200.domain.usecase.ObserveOpponentStatusUseCase
import com.project200.domain.usecase.ObserveSocketMessagesUseCase
import com.project200.domain.usecase.SendSocketMessageUseCase
import com.project200.feature.chatting.chattingRoom.ChatInputState
import com.project200.feature.chatting.chattingRoom.ChattingRoomViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.runs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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

    @MockK
    private lateinit var observeChatConnectionUseCase: ObserveChatConnectionUseCase

    private val connection = MutableStateFlow(ChatSocketConnection())
    private val incomingMessages = MutableSharedFlow<ChattingMessage>(extraBufferCapacity = 1)

    private lateinit var viewModel: ChattingRoomViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val sampleMessage =
        ChattingMessage(
            chatId = 1L,
            content = "안녕하세요",
            sentAt = LocalDateTime.of(2025, 1, 1, 10, 0),
            isMine = false,
            chatType = "NORMAL",
            senderId = "opponent1",
            nickname = "상대방",
            profileUrl = null,
            thumbnailImageUrl = null,
            showProfile = true,
            showTime = true,
        )

    private val sampleMessageList =
        ChattingModel(
            messages = listOf(sampleMessage),
            hasNext = false,
            opponentActive = true,
            blockActive = false,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { observeSocketMessagesUseCase() } returns incomingMessages
        every { observeOpponentStatusUseCase() } returns MutableSharedFlow<OpponentStatus>()
        every { observeChatConnectionUseCase() } returns connection
        viewModel =
            ChattingRoomViewModel(
                getChatMessagesUseCase,
                getNewChatMessagesUseCase,
                exitChatRoomUseCase,
                connectChatRoomUseCase,
                disconnectChatRoomUseCase,
                observeSocketMessagesUseCase,
                observeOpponentStatusUseCase,
                sendSocketMessageUseCase,
                observeChatConnectionUseCase,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setId - 초기 메시지를 로드한다`() =
        runTest {
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
    fun `setId - 메시지 로드 실패 시 토스트 이벤트 발생`() =
        runTest {
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
    fun `sendMessage - 빈 메시지는 전송하지 않는다`() =
        runTest {
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
    fun `sendMessage - 메시지를 전송한다`() =
        runTest {
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
    fun `exitChatRoom - 채팅방 나가기를 호출한다`() =
        runTest {
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
    fun `connectAndSync - 소켓 연결 및 누락 메시지를 동기화한다`() =
        runTest {
            // Given
            coEvery { getChatMessagesUseCase(1L, null, 30) } returns BaseResult.Success(sampleMessageList)
            every { connectChatRoomUseCase(1L) } just runs
            coEvery { getNewChatMessagesUseCase(1L, 1L) } returns
                BaseResult.Success(
                    ChattingModel(
                        hasNext = false,
                        opponentActive = true,
                        blockActive = false,
                        messages = emptyList(),
                    ),
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
    fun `disconnect - 소켓 연결을 해제한다`() =
        runTest {
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
    fun `loadPreviousMessages - 이전 메시지가 없으면 조회하지 않는다`() =
        runTest {
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

    @Test
    fun `준비 상태는 최초 이력과 동기화와 해당 방 소켓이 모두 성공해야 한다`() =
        runTest {
            backgroundScope.launch { viewModel.isReady.collect {} }
            val initial = CompletableDeferred<BaseResult<ChattingModel>>()
            val sync = CompletableDeferred<BaseResult<ChattingModel>>()
            coEvery { getChatMessagesUseCase(1L, null, 30) } coAnswers { initial.await() }
            coEvery { getNewChatMessagesUseCase(1L, 1L) } coAnswers { sync.await() }
            every { connectChatRoomUseCase(1L) } just runs

            viewModel.setId(1L, "opponent1")
            viewModel.connectAndSync()
            connection.value = ChatSocketConnection(1L, ChatSocketStatus.Connected)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(viewModel.isReady.value).isFalse()

            initial.complete(BaseResult.Success(sampleMessageList))
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(viewModel.isReady.value).isFalse()
            sync.complete(BaseResult.Success(sampleMessageList.copy(messages = emptyList())))
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(viewModel.isReady.value).isTrue()

            connection.value = ChatSocketConnection(2L, ChatSocketStatus.Connected)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(viewModel.isReady.value).isFalse()
            connection.value = ChatSocketConnection(1L, ChatSocketStatus.Closing)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(viewModel.isReady.value).isFalse()
        }

    @Test
    fun `최초 이력이 차단 상태이면 소켓 연결 후에도 준비되지 않는다`() =
        runTest {
            backgroundScope.launch { viewModel.isReady.collect {} }
            coEvery { getChatMessagesUseCase(1L, null, 30) } returns
                BaseResult.Success(sampleMessageList.copy(blockActive = true))
            coEvery { getNewChatMessagesUseCase(1L, 1L) } returns BaseResult.Success(sampleMessageList)
            every { connectChatRoomUseCase(1L) } just runs

            viewModel.setId(1L, "opponent1")
            viewModel.connectAndSync()
            connection.value = ChatSocketConnection(1L, ChatSocketStatus.Connected)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(viewModel.chatState.value).isEqualTo(
                ChatInputState.OpponentBlocked,
            )
            assertThat(viewModel.isReady.value).isFalse()
        }

    @Test
    fun `이력 동기화 실패는 준비 상태로 바뀌지 않는다`() =
        runTest {
            backgroundScope.launch { viewModel.isReady.collect {} }
            coEvery { getChatMessagesUseCase(1L, null, 30) } returns BaseResult.Success(sampleMessageList)
            coEvery { getNewChatMessagesUseCase(1L, 1L) } returns BaseResult.Error("ERROR", "Failed")
            every { connectChatRoomUseCase(1L) } just runs

            viewModel.setId(1L, "opponent1")
            viewModel.connectAndSync()
            connection.value = ChatSocketConnection(1L, ChatSocketStatus.Connected)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(viewModel.isReady.value).isFalse()
        }

    @Test
    fun `최초 REST 100과 조기 소켓 102 사이의 누락 101을 동기화한다`() =
        runTest {
            val initial = CompletableDeferred<BaseResult<ChattingModel>>()
            val snapshotMessage = sampleMessage.copy(chatId = 100L)
            val socketMessage = sampleMessage.copy(chatId = 102L, content = "live")
            val missedMessage = sampleMessage.copy(chatId = 101L, content = "missed")
            coEvery { getChatMessagesUseCase(1L, null, 30) } coAnswers { initial.await() }
            coEvery { getNewChatMessagesUseCase(1L, any()) } returns
                BaseResult.Success(sampleMessageList.copy(messages = listOf(missedMessage)))
            every { connectChatRoomUseCase(1L) } just runs

            viewModel.setId(1L, "opponent1")
            viewModel.connectAndSync()
            testDispatcher.scheduler.runCurrent()
            incomingMessages.emit(socketMessage)
            testDispatcher.scheduler.runCurrent()
            assertThat(viewModel.messages.value.map { it.chatId }).containsExactly(102L)

            initial.complete(BaseResult.Success(sampleMessageList.copy(messages = listOf(snapshotMessage))))
            testDispatcher.scheduler.runCurrent()
            coVerify(exactly = 1) { getNewChatMessagesUseCase(1L, 100L) }
            assertThat(viewModel.messages.value.map { it.chatId }).containsExactly(100L, 101L, 102L).inOrder()

            coEvery { getNewChatMessagesUseCase(1L, 101L) } returns
                BaseResult.Success(sampleMessageList.copy(messages = listOf(socketMessage)))
            viewModel.syncMissedMessages()
            testDispatcher.scheduler.runCurrent()
            coVerify(exactly = 1) { getNewChatMessagesUseCase(1L, 101L) }
            assertThat(viewModel.messages.value.map { it.chatId }).containsExactly(100L, 101L, 102L).inOrder()
        }

    @Test
    fun `상대 프로필에서 유예 시간 내 복귀하면 소켓 재연결 없이 이력을 동기화한다`() =
        runTest {
            backgroundScope.launch { viewModel.isReady.collect {} }
            val missedMessage = sampleMessage.copy(chatId = 2L, content = "missed")
            coEvery { getChatMessagesUseCase(1L, null, 30) } returns BaseResult.Success(sampleMessageList)
            coEvery { getNewChatMessagesUseCase(1L, 1L) } returns
                BaseResult.Success(sampleMessageList.copy(messages = emptyList()))
            every { connectChatRoomUseCase(1L) } just runs
            every { disconnectChatRoomUseCase() } just runs

            viewModel.setId(1L, "opponent1")
            viewModel.connectAndSync()
            connection.value = ChatSocketConnection(1L, ChatSocketStatus.Connected)
            testDispatcher.scheduler.runCurrent()
            assertThat(viewModel.isReady.value).isTrue()

            // 프로필 이동으로 STOPPED가 된 뒤 같은 방의 뷰를 다시 만듭니다.
            viewModel.scheduleDisconnect()
            testDispatcher.scheduler.advanceTimeBy(1000L)
            coEvery { getNewChatMessagesUseCase(1L, 1L) } returns
                BaseResult.Success(sampleMessageList.copy(messages = listOf(missedMessage)))
            viewModel.setId(1L, "opponent1")
            viewModel.connectAndSync()
            testDispatcher.scheduler.runCurrent()

            assertThat(viewModel.isReady.value).isTrue()
            assertThat(viewModel.messages.value.map { it.chatId }).containsExactly(1L, 2L).inOrder()
            coVerify(exactly = 2) { getChatMessagesUseCase(1L, null, 30) }
            coVerify(exactly = 2) { getNewChatMessagesUseCase(1L, 1L) }
            coVerify(exactly = 1) { connectChatRoomUseCase(1L) }
            coVerify(exactly = 0) { disconnectChatRoomUseCase() }
        }
}
