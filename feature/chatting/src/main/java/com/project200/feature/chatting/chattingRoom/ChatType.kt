package com.project200.feature.chatting.chattingRoom

enum class ChatType(val str: String) {
    USER("USER"),
    SYSTEM("SYSTEM"),
}

sealed class ChatInputState {
    object Active : ChatInputState() // 활성 상태

    object OpponentBlocked : ChatInputState() // 상대방 차단 상태

    object OpponentLeft : ChatInputState() // 상대방 나감 상태
}
