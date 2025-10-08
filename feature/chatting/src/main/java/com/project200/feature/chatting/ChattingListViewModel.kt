package com.project200.feature.chatting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.ChattingRoom
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChattingListViewModel @Inject constructor(

) : ViewModel() {
    private val _chattingRooms = MutableStateFlow<List<ChattingRoom>>(emptyList())
    val chattingRooms: StateFlow<List<ChattingRoom>> = _chattingRooms

    init {
        fetchChattingRooms()
    }

    // 채팅방 리스트를 가져오는 함수 (지금은 임시 데이터 생성)
    private fun fetchChattingRooms() {
        viewModelScope.launch {
            // TODO: API를 호출하여 데이터를 가져옵니다.
            val mockData = listOf(
                ChattingRoom(
                    id = 1,
                    nickname = "강남 공인중개사",
                    profileImageUrl = "url_to_profile_image_1", // 실제 이미지를 사용하려면 여기에 URL을 넣으세요.
                    thumbnailImageUrl = "url_to_thumbnail_image_1",
                    lastMessage = "네, 그 집 아직 계약 가능합니다. 언제 보러 오시겠어요?",
                    lastChattedAt = "2024-10-28T15:30:00Z",
                    unreadCount = 2
                ),
                ChattingRoom(
                    id = 2,
                    nickname = "집주인",
                    profileImageUrl = "url_to_profile_image_2",
                    thumbnailImageUrl = "url_to_thumbnail_image_2",
                    lastMessage = "월세 입금 확인했습니다. 감사합니다.",
                    lastChattedAt = "2024-10-28T11:15:00Z",
                    unreadCount = 0
                ),
                ChattingRoom(
                    id = 3,
                    nickname = "홍대 원룸 문의",
                    profileImageUrl = "url_to_profile_image_3",
                    thumbnailImageUrl = "url_to_thumbnail_image_3",
                    lastMessage = "사진을 보냈습니다.",
                    lastChattedAt = "2024-10-27T18:45:00Z",
                    unreadCount = 1
                ),
                ChattingRoom(
                    id = 4,
                    nickname = "이사 센터",
                    profileImageUrl = "url_to_profile_image_4",
                    thumbnailImageUrl = "url_to_thumbnail_image_4",
                    lastMessage = "견적 보내드렸습니다. 확인 후 연락주세요.",
                    lastChattedAt = "2024-10-26T09:00:00Z",
                    unreadCount = 0
                ),
                ChattingRoom(
                    id = 5,
                    nickname = "성수동 오피스텔",
                    profileImageUrl = "url_to_profile_image_5",
                    thumbnailImageUrl = "url_to_thumbnail_image_5",
                    lastMessage = "네, 내일 오후 2시에 뵙겠습니다.",
                    lastChattedAt = "2024-10-25T14:22:00Z",
                    unreadCount = 5
                )
            )
            _chattingRooms.value = mockData
        }
    }
}