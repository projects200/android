package com.project200.feature.chatting.chattingRoom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.common.utils.ClockProvider
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ChattingMessage
import com.project200.domain.model.SocketType
import com.project200.domain.usecase.ConnectChatRoomUseCase
import com.project200.domain.usecase.DisconnectChatRoomUseCase
import com.project200.domain.usecase.ExitChatRoomUseCase
import com.project200.domain.usecase.GetChatMessagesUseCase
import com.project200.domain.usecase.GetNewChatMessagesUseCase
import com.project200.domain.usecase.ObserveSocketMessagesUseCase
import com.project200.domain.usecase.SendChatMessageUseCase
import com.project200.domain.usecase.SendSocketMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChattingRoomViewModel
    @Inject
    constructor(
        private val getChatMessagesUseCase: GetChatMessagesUseCase,
        private val getNewChatMessagesUseCase: GetNewChatMessagesUseCase,
        private val sendChatMessageUseCase: SendChatMessageUseCase,
        private val exitChatRoomUseCase: ExitChatRoomUseCase,
        private val clockProvider: ClockProvider,
        private val connectChatRoomUseCase: ConnectChatRoomUseCase,
        private val disconnectChatRoomUseCase: DisconnectChatRoomUseCase,
        private val observeSocketMessagesUseCase: ObserveSocketMessagesUseCase,
        private val sendSocketMessageUseCase: SendSocketMessageUseCase,
    ) : ViewModel() {
        private val _messages = MutableStateFlow<List<ChattingMessage>>(emptyList())
        val messages = _messages.asStateFlow()

        private val _chatState = MutableStateFlow<ChatInputState>(ChatInputState.Active)
        val chatState: StateFlow<ChatInputState> = _chatState.asStateFlow()

        private val _exitResult = MutableSharedFlow<BaseResult<Unit>>()
        val exitResult: SharedFlow<BaseResult<Unit>> = _exitResult

        private val _toast = MutableSharedFlow<String>()
        val toast: SharedFlow<String> = _toast

        private var chatRoomId: Long = DEFAULT_ID
        private var opponentMemberId: String = ""
        private var prevChatId: Long? = null // 이전 메시지 조회를 위한 가장 오래된 메시지 ID
        private var lastChatId: Long? = null // 새 메시지 조회를 위한 마지막 메시지 ID
        var hasNextMessages: Boolean = true // 더 로드할 메시지가 있는지 여부

        init {
            viewModelScope.launch {
                observeSocketMessagesUseCase().collect { payload ->
                    // TALK 타입이고 내용이 있을 때 처리
                    val content = payload.content ?: return@collect
                    if (payload.type == SocketType.TALK) {
                        // TODO: SocketPayload의 content가 JSON String이라면 파싱해서 ChattingMessage로 변환
                        // 임시 ChattingMessage 객체
                        val newMessage = ChattingMessage(
                            chatId = -1, // 소켓 전용 ID 로직이 필요할 수 있음
                            senderId = opponentMemberId, // 상대방이 보낸 것으로 간주
                            nickname = "상대방", // 닉네임 정보가 없다면 API 재조회 필요할 수도 있음
                            profileUrl = null,
                            thumbnailImageUrl = null,
                            content = content,
                            chatType = "USER",
                            sentAt = clockProvider.localDateTimeNow(),
                            isMine = false
                        )

                        // 리스트에 추가
                        addMessage(newMessage)
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
            loadInitialMessages(chatRoomId)
        }

        // 소켓 연결 및 공백 채우기
        fun connectAndSync() {
            if (chatRoomId == DEFAULT_ID) return
            // 소켓 연결 시도
            connectChatRoomUseCase(chatRoomId)
            // 소켓이 끊겨있던 동안 온 메시지 가져오기
            syncMissedMessages()
        }

        fun disconnect() {
            disconnectChatRoomUseCase()
        }

        // 메시지 추가
        private fun addMessage(newMessage: ChattingMessage) {
            val currentList = _messages.value
            if (currentList.none { it.content == newMessage.content && it.sentAt == newMessage.sentAt }) {
                // chatId가 있다면 chatId로 비교, 없다면 내용+시간으로 비교
                updateAndEmitMessages(currentList + newMessage)
                // TODO: 소켓으로 받은 메시지도 lastChatId 갱신
            }
        }

        private fun updateAndEmitMessages(updatedList: List<ChattingMessage>) {
            _messages.value = processMessagesForGrouping(updatedList)
        }

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
        private fun loadInitialMessages(id: Long) {
            viewModelScope.launch {
                // 초기 메시지 로드 (prevChatId 없이 최신 메시지부터 size 만큼 가져옴)
                when (val result = getChatMessagesUseCase(id, size = LOAD_SIZE)) {
                    is BaseResult.Success -> {
                        val chattingModel = result.data
                        updateAndEmitMessages(chattingModel.messages)
                        hasNextMessages = chattingModel.hasNext
                        prevChatId = chattingModel.messages.firstOrNull()?.chatId // 가장 오래된 메시지의 ID를 저장
                        lastChatId = chattingModel.messages.lastOrNull()?.chatId

                        updateChatState(chattingModel.blockActive, chattingModel.opponentActive)
                    }
                    is BaseResult.Error -> {
                        _toast.emit(result.message.toString())
                    }
                }
            }
        }

        /**
         * 소켓 연결이 끊겨있는 사이 온 새 메시지를 받아오는 함수
         */
        fun syncMissedMessages() {
            if (chatRoomId == DEFAULT_ID) return
            viewModelScope.launch {
                when (val result = getNewChatMessagesUseCase(chatRoomId, lastChatId)) {
                    is BaseResult.Success -> {
                        if (result.data.messages.isNotEmpty()) {
                            // 기존 메시지 리스트에 새로운 메시지를 추가
                            val currentMessages = _messages.value.toMutableList()
                            // 중복 방지
                            val uniqueNewMessages =
                                result.data.messages.filter { newMessage ->
                                    !currentMessages.any { it.chatId == newMessage.chatId }
                                }
                            if (uniqueNewMessages.isNotEmpty()) {
                                lastChatId = uniqueNewMessages.lastOrNull()?.chatId
                                updateAndEmitMessages(currentMessages + uniqueNewMessages)
                            }
                        }
                        updateChatState(result.data.blockActive, result.data.opponentActive)
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

            val messageToSend =
                ChattingMessage(
                    chatId = DEFAULT_ID, // 임시 아이디
                    senderId = "my_user_id",
                    nickname = "나",
                    profileUrl = null,
                    thumbnailImageUrl = null,
                    content = text,
                    chatType = "USER",
                    sentAt = clockProvider.localDateTimeNow(),
                    isMine = true,
                )

            viewModelScope.launch {
                when (val result = sendChatMessageUseCase(chatRoomId, text)) {
                    is BaseResult.Success -> {
                        // 서버로부터 받은 chatId로 설정
                        val confirmedMessage =
                            messageToSend.copy(
                                chatId = result.data,
                            )
                        updateAndEmitMessages(_messages.value + confirmedMessage)
                    }
                    is BaseResult.Error -> {
                        _toast.emit(result.message.toString())
                    }
                }
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

        private fun updateChatState(blockActive: Boolean, opponentActive: Boolean) {
            _chatState.value = when {
                blockActive -> ChatInputState.OpponentBlocked
                !opponentActive -> ChatInputState.OpponentLeft
                else -> ChatInputState.Active
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
        }
    }
