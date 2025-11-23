package com.project200.common.utils

import kotlinx.coroutines.flow.StateFlow

/**
 * 앱 전체에서 현재 활성화된 채팅방의 상태를 관리하는 리포지토리 인터페이스
 */
interface ChatRoomStateRepository {
    /**
     * 현재 활성화된 채팅방의 ID를 StateFlow 형태로 제공합니다.
     * 활성화된 채팅방이 없으면 null입니다.
     */
    val activeChatRoomId: StateFlow<Long?>

    /**
     * 현재 활성화된 채팅방의 ID를 설정합니다.
     * @param roomId 채팅방에서 나갈 때는 null을 전달합니다.
     */
    fun setActiveChatRoomId(roomId: Long?)
}
