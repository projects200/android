package com.project200.feature.chatting.chattingRoom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ChatSocketStatus
import com.project200.domain.model.ChattingMessage
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ChattingRoomViewModel
    @Inject
    constructor(
        private val getChatMessagesUseCase: GetChatMessagesUseCase,
        private val getNewChatMessagesUseCase: GetNewChatMessagesUseCase,
        private val exitChatRoomUseCase: ExitChatRoomUseCase,
        private val connectChatRoomUseCase: ConnectChatRoomUseCase,
        private val disconnectChatRoomUseCase: DisconnectChatRoomUseCase,
        private val observeSocketMessagesUseCase: ObserveSocketMessagesUseCase,
        private val observeOpponentStatusUseCase: ObserveOpponentStatusUseCase,
        private val sendSocketMessageUseCase: SendSocketMessageUseCase,
        observeChatConnectionUseCase: ObserveChatConnectionUseCase,
    ) : ViewModel() {
        private val _messages = MutableStateFlow<List<ChattingMessage>>(emptyList())
        val messages = _messages.asStateFlow()

        private val _chatState = MutableStateFlow<ChatInputState>(ChatInputState.Active)
        val chatState: StateFlow<ChatInputState> = _chatState.asStateFlow()

        private val historyLoaded = MutableStateFlow(false)
        private val historySynced = MutableStateFlow(false)
        private val socketConnection = observeChatConnectionUseCase()
        val isReady =
            combine(
                historyLoaded,
                historySynced,
                socketConnection,
                chatState,
            ) { loaded, synced, connection, state ->
                loaded && synced && connection.roomId == chatRoomId &&
                    connection.status == ChatSocketStatus.Connected && state == ChatInputState.Active
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

        private val _exitResult = MutableSharedFlow<BaseResult<Unit>>()
        val exitResult: SharedFlow<BaseResult<Unit>> = _exitResult

        private val _toast = MutableSharedFlow<String>()
        val toast: SharedFlow<String> = _toast

        private var chatRoomId: Long = DEFAULT_ID
        private var opponentMemberId: String = ""
        private var prevChatId: Long? = null // 이전 메시지 조회를 위한 가장 오래된 메시지 ID
        private var lastChatId: Long? = null // REST로 확인한 마지막 메시지 ID (소켓 수신은 커서를 전진시키지 않음)
        var hasNextMessages: Boolean = true // 더 로드할 메시지가 있는지 여부

        private var disconnectJob: Job? = null
        private var initialHistoryJob: Job? = null

        init {
            viewModelScope.launch {
                observeSocketMessagesUseCase().collect { chat ->
                    // 리스트에 추가
                    addMessage(chat)
                }
            }

            viewModelScope.launch {
                observeOpponentStatusUseCase().collect { status ->
                    when (status) {
                        is OpponentStatus.Left -> _chatState.value = ChatInputState.OpponentLeft
                        is OpponentStatus.Blocked -> _chatState.value = ChatInputState.OpponentBlocked
                    }
                }
            }
        }

        fun setId(
            chatRoomId: Long,
            opponentId: String,
        ) {
            this.chatRoomId = chatRoomId
            this.opponentMemberId = opponentId
            historyLoaded.value = false
            historySynced.value = false
            initialHistoryJob = loadInitialMessages(chatRoomId)
        }

        // 소켓 연결 및 공백 채우기 (유예 시간 내 복귀 시 재연결 생략)
        fun connectAndSync() {
            if (chatRoomId == DEFAULT_ID) return

            val wasInGracePeriod = disconnectJob?.isActive == true
            disconnectJob?.cancel()
            disconnectJob = null

            if (!wasInGracePeriod) {
                // 유예 시간 만료 또는 최초 연결 → 전체 재연결
                connectChatRoomUseCase(chatRoomId)
            }
            if (!wasInGracePeriod || !historySynced.value) {
                // 뷰 재생성으로 이력이 초기화됐다면 기존 소켓을 유지한 채 동기화합니다.
                syncMissedMessages()
            }
        }

        fun scheduleDisconnect() {
            disconnectJob?.cancel()
            disconnectJob =
                viewModelScope.launch {
                    Timber.w("[MEASURE] 연결 해제 유예 시작 (${DISCONNECT_GRACE_PERIOD_MS}ms)")
                    delay(DISCONNECT_GRACE_PERIOD_MS)
                    Timber.w("[MEASURE] 유예 시간 만료, 연결 해제")
                    disconnectChatRoomUseCase()
                }
        }

        fun disconnect() {
            disconnectJob?.cancel()
            disconnectJob = null
            disconnectChatRoomUseCase()
        }

        // 메시지 추가
        private fun addMessage(newMessage: ChattingMessage) {
            val currentList = _messages.value
            if (currentList.none { it.content == newMessage.content && it.sentAt == newMessage.sentAt }) {
                updateAndEmitMessages(currentList + newMessage)
            }
        }

        // 메시지 목록 업데이트 및 방출
        private fun updateAndEmitMessages(updatedList: List<ChattingMessage>) {
            _messages.value = processMessagesForGrouping(updatedList)
        }

        /**
         * 메시지 그룹화 처리
         */
        private fun processMessagesForGrouping(messages: List<ChattingMessage>): List<ChattingMessage> {
            if (messages.isEmpty()) return emptyList()

            return messages.mapIndexed { index, current ->
                val prev = messages.getOrNull(index - 1)
                val next = messages.getOrNull(index + 1)

                val isFirstInGroup =
                    prev == null || prev.chatType == ChatType.SYSTEM.str ||
                        current.isMine != prev.isMine || current.sentAt.minute != prev.sentAt.minute

                val isLastInGroup =
                    next == null || next.chatType == ChatType.SYSTEM.str ||
                        current.isMine != next.isMine || current.sentAt.minute != next.sentAt.minute

                // copy()를 사용하여 새로운 객체를 생성. DiffUtil이 변화를 감지할 수 있게 함.
                val newMessage =
                    current.copy(
                        showProfile = isFirstInGroup,
                        showTime = isLastInGroup,
                    )

                newMessage
            }
        }

        /**
         * 초기 메세지 목록 조회
         */
        private fun loadInitialMessages(id: Long): Job =
            viewModelScope.launch {
                when (val result = getChatMessagesUseCase(id, size = LOAD_SIZE)) {
                    is BaseResult.Success -> {
                        val chattingModel = result.data
                        // 초기 REST 응답보다 먼저 도착한 실제 소켓 메시지를 덮어쓰지 않습니다.
                        updateAndEmitMessages(
                            (chattingModel.messages + _messages.value)
                                .distinctBy { it.chatId }
                                .sortedBy { it.chatId },
                        )
                        hasNextMessages = chattingModel.hasNext
                        prevChatId = chattingModel.messages.minOfOrNull { it.chatId }
                        lastChatId = chattingModel.messages.maxOfOrNull { it.chatId }
                        _chatState.value =
                            when {
                                chattingModel.blockActive -> ChatInputState.OpponentBlocked
                                !chattingModel.opponentActive -> ChatInputState.OpponentLeft
                                else -> _chatState.value
                            }
                        historyLoaded.value = true
                    }
                    is BaseResult.Error -> _toast.emit(result.message.toString())
                }
            }

        /**
         * 소켓 연결이 끊겨있는 사이 온 새 메시지를 받아오는 함수
         */
        fun syncMissedMessages() {
            if (chatRoomId == DEFAULT_ID) return
            historySynced.value = false
            viewModelScope.launch {
                initialHistoryJob?.join()
                if (!historyLoaded.value) return@launch
                val startTime = System.currentTimeMillis()
                when (val result = getNewChatMessagesUseCase(chatRoomId, lastChatId)) {
                    is BaseResult.Success -> {
                        if (result.data.messages.isNotEmpty()) {
                            // 실시간 소켓 메시지 사이에 빠진 이력을 ID 순서로 병합합니다.
                            updateAndEmitMessages(
                                (_messages.value + result.data.messages)
                                    .distinctBy { it.chatId }
                                    .sortedBy { it.chatId },
                            )
                            lastChatId = result.data.messages.maxOf { it.chatId }
                        } else {
                            Timber.w("[MEASURE] 누락 메시지 없음 (0건), 소요시간: ${System.currentTimeMillis() - startTime}ms")
                        }
                        _chatState.value =
                            when {
                                result.data.blockActive -> ChatInputState.OpponentBlocked
                                !result.data.opponentActive -> ChatInputState.OpponentLeft
                                else -> _chatState.value
                            }
                        historySynced.value = true
                    }

                    is BaseResult.Error -> {
                        _toast.emit(result.message.toString())
                    }
                }
            }
        }

        /**
         * 사용자가 보낸 메시지를 리스트에 추가하는 함수
         */
        fun sendMessage(text: String) {
            if (text.isBlank()) return
            if (chatRoomId == DEFAULT_ID) return

            viewModelScope.launch {
                sendSocketMessageUseCase(text)
            }
        }

        fun loadPreviousMessages() {
            if (!hasNextMessages || chatRoomId == DEFAULT_ID || prevChatId == null) {
                return
            }

            viewModelScope.launch {
                when (val result = getChatMessagesUseCase(chatRoomId, prevChatId, LOAD_SIZE)) {
                    is BaseResult.Success -> {
                        if (result.data.messages.isNotEmpty()) {
                            // 기존 리스트 앞에 새롭게 불러온 메시지 추가
                            // 기존 메시지의 가장 오래된 ID를 업데이트
                            updateAndEmitMessages(result.data.messages + _messages.value)
                            prevChatId = result.data.messages.firstOrNull()?.chatId
                        }
                        hasNextMessages = result.data.hasNext
                    }

                    is BaseResult.Error -> {
                        _toast.emit(result.message.toString())
                    }
                }
            }
        }

        fun exitChatRoom() {
            viewModelScope.launch {
                _exitResult.emit(exitChatRoomUseCase(chatRoomId))
            }
        }

        override fun onCleared() {
            super.onCleared()
            disconnect()
        }

        companion object {
            const val DEFAULT_ID = -1L
            const val LOAD_SIZE = 30 // 초기 로드 메시지 개수
            private const val DISCONNECT_GRACE_PERIOD_MS = 5000L
        }
    }
