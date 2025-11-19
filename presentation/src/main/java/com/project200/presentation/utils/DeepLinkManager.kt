package com.project200.presentation.utils

import android.net.Uri

object DeepLinkManager {
    private const val SCHEME = "app"
    private const val AUTHORITY_CHATTING = "chatting"

    /**
     * 채팅방으로 이동하는 딥링크 Uri를 생성합니다.
     * @param chatRoomId 채팅방의 고유 ID
     * @param nickname 상대방의 닉네임
     * @param memberId 상대방의 멤버 ID
     * @return 생성된 Uri 객체 (예: app://chatting/room/123/라이언/456)
     */
    fun createChatRoomUri(
        chatRoomId: String,
        nickname: String,
        memberId: String,
    ): Uri {
        return Uri.Builder()
            .scheme(SCHEME)
            .authority(AUTHORITY_CHATTING)
            .appendPath("room")
            .appendPath(chatRoomId)
            .appendPath(nickname)
            .appendPath(memberId)
            .build()
    }
}
