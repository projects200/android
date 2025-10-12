package com.project200.feature.chatting.chattingRoom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.ChattingMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class ChattingRoomViewModel @Inject constructor(

): ViewModel() {

    private val _messages = MutableStateFlow<List<ChattingMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    // 새 메시지의 고유 ID 생성을 위한 카운터
    private var newMessageCounter = NEW_MESSAGE_DEFAULT_ID

    init {
        loadInitialMessages()
    }

    private fun updateAndEmitMessages(updatedList: List<ChattingMessage>) {
        _messages.value = processMessagesForGrouping(updatedList)
    }

    private fun processMessagesForGrouping(messages: List<ChattingMessage>): List<ChattingMessage> {
        if (messages.isEmpty()) return emptyList()

        return messages.mapIndexed { index, current ->
            val prev = messages.getOrNull(index - 1)
            val next = messages.getOrNull(index + 1)

            val isFirstInGroup = prev == null || prev.chatType == ChatType.SYSTEM.str ||
                    current.senderId != prev.senderId || current.sentAt.minute != prev.sentAt.minute

            val isLastInGroup = next == null || next.chatType == ChatType.SYSTEM.str ||
                    current.senderId != next.senderId || current.sentAt.minute != next.sentAt.minute

            // copy()를 사용하여 새로운 객체를 생성. DiffUtil이 변화를 감지할 수 있게 함.
            val newMessage = current.copy(
                showProfile = isFirstInGroup,
                showTime = isLastInGroup
            )

            newMessage
        }
    }

    private fun loadInitialMessages() {
        // 임시 더미 데이터
        val sampleData = listOf(
            ChattingMessage(1, "user_2", "김땡구리", null, null, "안녕하세요안녕하세요안녕하세요안녕하세요안녕하세요안녕하세요안녕하세요안녕하세요안녕하세요안녕하세요안녕하세요안녕하세요", "USER", LocalDateTime.now().withHour(10).withMinute(45), false),
            ChattingMessage(2, "user_1", "나", null, null, "안녕하세요", "USER", LocalDateTime.now().withHour(10).withMinute(45), true),
            ChattingMessage(3, "user_2", "김땡구리", null, null, "반갑습니다", "USER", LocalDateTime.now().withHour(10).withMinute(45), false),
            ChattingMessage(4, "user_2", "김땡구리", null, null, "김땡구리 입니다.", "USER", LocalDateTime.now().withHour(10).withMinute(45), false),
            ChattingMessage(5, "user_2", "김땡구리", null, null, "반갑습니다", "USER", LocalDateTime.now().withHour(10).withMinute(46), false),
            ChattingMessage(6, "user_2", "김땡구리", null, null, "김땡구리 입니다.", "USER", LocalDateTime.now().withHour(10).withMinute(46), false),
            ChattingMessage(7, "system_1", "system", null, null, "(상대방 닉네임)님이 채팅방을 나갔습니다.", "SYSTEM", LocalDateTime.now().withHour(10).withMinute(47), false),
            ChattingMessage(8, "system_1", "system", null, null, "(상대방 닉네임)님이 채팅방에 입장했습니다.", "SYSTEM", LocalDateTime.now().withHour(10).withMinute(48), false)
        )
        updateAndEmitMessages(sampleData)
    }


    /**
     * 새 메시지를 받아오는 폴링 함수
     */
    fun startPolling() {
        viewModelScope.launch {
            while (true) {
                delay(2000) // 2초 대기
                val newMessage = listOf<ChattingMessage>(ChattingMessage(
                    chatId = newMessageCounter--,
                    senderId = "user_1",
                    nickname = "나",
                    profileUrl = null,
                    thumbnailImageUrl = null,
                    content = "새 메시지 입니다. ${LocalDateTime.now()}",
                    chatType = "USER",
                    sentAt = LocalDateTime.now(),
                    isMine = false
                ))
                // 기존 리스트의 맨 뒤에 새 메시지 추가
                updateAndEmitMessages(_messages.value + newMessage)
            }
        }
    }

    /**
     * 사용자가 보낸 메시지를 리스트에 추가하는 함수
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // 더미 데이터
        val myMessage = listOf<ChattingMessage>(ChattingMessage(
            chatId = newMessageCounter++,
            senderId = "user_1",
            nickname = "나",
            profileUrl = null,
            thumbnailImageUrl = null,
            content = text,
            chatType = "USER",
            sentAt = LocalDateTime.now(),
            isMine = true
        ))
        // 기존 리스트의 맨 뒤에 내가 보낸 메시지 추가
        updateAndEmitMessages(_messages.value + myMessage)

        //TODO: 메세지 전송 api 호출
    }


    fun loadPreviousMessages() {
        viewModelScope.launch {
            // 더미 데이터
            val myMessage = listOf<ChattingMessage>(ChattingMessage(
                chatId = newMessageCounter++,
                senderId = "user_1",
                nickname = "나",
                profileUrl = null,
                thumbnailImageUrl = null,
                content = "이전 메세지",
                chatType = "USER",
                sentAt = LocalDateTime.now(),
                isMine = true
            ))
            // 기존 리스트의 맨 뒤에 내가 보낸 메시지 추가
            updateAndEmitMessages(myMessage + _messages.value)
            // TODO: 이전 페이지의 메시지를 가져오는 api 호출 구현
        }
    }

    companion object {
        const val NEW_MESSAGE_DEFAULT_ID = -1L
    }
}