package com.project200.feature.chatting.chattingRoom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.common.utils.ClockProvider
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ChattingMessage
import com.project200.domain.usecase.GetChatMessagesUseCase
import com.project200.domain.usecase.GetNewChatMessagesUseCase
import com.project200.domain.usecase.SendChatMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
        private val clockProvider: ClockProvider,
    ) : ViewModel() {
        private val _messages = MutableStateFlow<List<ChattingMessage>>(emptyList())
        val messages = _messages.asStateFlow()

        // 이전 메시지 로딩 상태를 관리하는 StateFlow 추가
        private val _isLoadingPreviousMessages = MutableStateFlow(false)
        val isLoadingPreviousMessages = _isLoadingPreviousMessages.asStateFlow()

        private var chatRoomId: Long = DEFAULT_ID
        private var prevChatId: Long? = null // 이전 메시지 조회를 위한 가장 오래된 메시지 ID
        private var lastChatId: Long? = null // 새 메시지 조회를 위한 마지막 메시지 ID
        var hasNextMessages: Boolean = true // 더 로드할 메시지가 있는지 여부

        fun setChatRoomId(id: Long) {
            chatRoomId = id
            loadInitialMessages(id)
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
                    }
                    is BaseResult.Error -> {
                        // TODO: 에러 처리 (토스트 메시지, 로그 등)
                    }
                }
            }
        }

        /**
         * 새 메시지를 받아오는 폴링 함수
         */
        fun getNewMessages() {
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
                    }

                    is BaseResult.Error -> {
                        // TODO: 에러 처리
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
                    senderId = "my_user_id", // TODO: 실제 로그인한 사용자의 ID로 교체해야 합니다.
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
                        // TODO: 전송 실패 시 처리
                    }
                }
            }
        }

        fun loadPreviousMessages() {
            if (!hasNextMessages || chatRoomId == DEFAULT_ID || prevChatId == null) {
                return
            }

            viewModelScope.launch {
                _isLoadingPreviousMessages.value = true // 로딩 시작
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
                        // TODO: 에러 처리
                    }
                }

                _isLoadingPreviousMessages.value = false
            }
        }

        companion object {
            const val DEFAULT_ID = -1L
            const val LOAD_SIZE = 30 // 초기 로드 메시지 개수
        }
    }
