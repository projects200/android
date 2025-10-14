package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.GetChattingMessagesDTO
import com.project200.data.dto.GetNewChattingMessagesDTO
import com.project200.data.dto.PostChatMessageRequest
import com.project200.data.dto.PostMessageResponse
import com.project200.data.mapper.toModel
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ChattingMessage
import com.project200.domain.model.ChattingModel
import com.project200.domain.model.ChattingRoom
import com.project200.domain.repository.ChattingRepository
import kotlinx.coroutines.CoroutineDispatcher
import java.time.LocalDateTime
import javax.inject.Inject

class ChattingRepositoryImpl
@Inject
constructor(
    private val apiService: ApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ChattingRepository {

    // 채팅방
    override suspend fun getChattingRooms(): BaseResult<List<ChattingRoom>> {
        val dummyData =
            listOf(
                ChattingRoom(
                    id = 101,
                    nickname = "이영희",
                    profileImageUrl = "https://example.com/profile_b.jpg",
                    thumbnailImageUrl = null,
                    lastMessage = "네, 안녕하세요 철수씨",
                    lastChattedAt = LocalDateTime.now().minusHours(1),
                    unreadCount = 3,
                ),
                ChattingRoom(
                    id = 105,
                    nickname = "형",
                    profileImageUrl = null,
                    thumbnailImageUrl = null,
                    lastMessage = "넌 나가라.",
                    lastChattedAt = LocalDateTime.now().minusDays(1).withHour(11).withMinute(30),
                    unreadCount = 0,
                ),
                ChattingRoom(
                    id = 106,
                    nickname = "오랜 친구",
                    profileImageUrl = null,
                    thumbnailImageUrl = null,
                    lastMessage = "다음에 한 번 보자!",
                    lastChattedAt = LocalDateTime.now().minusDays(2),
                    unreadCount = 1,
                ),
                ChattingRoom(
                    id = 107,
                    nickname = "새로운 대화",
                    profileImageUrl = null,
                    thumbnailImageUrl = null,
                    lastMessage = "만나서 반갑습니다.",
                    lastChattedAt = LocalDateTime.now().minusMinutes(5),
                    unreadCount = 0,
                ),
            )

        return BaseResult.Success(dummyData)

        /* TODO: 실제 api 연결
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.getChattingRooms() },
            mapper = { dtoList: List<GetChattingRoomsDTO>? ->
                dtoList?.map { it.toModel() } ?: throw NoSuchElementException()
            },
        )*/
    }

    override suspend fun getChattingMessages(
        chatRoomId: Long,
        prevChatId: Long?,
        size: Int
    ): BaseResult<ChattingModel> {
        // 더미 데이터 추가
        // prevChatId가 null이면 초기 로드
        // prevChatId가 있으면 이전 메시지를 반환
        val messages = if (prevChatId == null) {
            // 초기 로드 (최신 메시지)
            listOf(
                ChattingMessage(11, "user_2", "상대방", null, null, "그래서 어떻게 할거야?", "USER", LocalDateTime.now().minusMinutes(3), false),
                ChattingMessage(12, "user_1", "나", null, null, "고민 좀 해볼게.", "USER", LocalDateTime.now().minusMinutes(2), true),
                ChattingMessage(13, "user_2", "상대방", null, null, "알았어.", "USER", LocalDateTime.now().minusMinutes(1), false),
            )
        } else {
            // 이전 메시지 로드
            listOf(
                ChattingMessage(1, "user_2", "상대방", null, null, "혹시 채팅 테스트 하시나요?", "USER", LocalDateTime.now().minusMinutes(20), false),
                ChattingMessage(2, "user_1", "나", null, null, "네 맞아요. 스크롤 기능 테스트 중입니다.", "USER", LocalDateTime.now().minusMinutes(19), true),
                ChattingMessage(3, "user_2", "상대방", null, null, "아하 그렇군요. 이전 메시지 불러오는 기능인가요?", "USER", LocalDateTime.now().minusMinutes(19), false),
                ChattingMessage(4, "user_1", "나", null, null, "네. 상단으로 스크롤했을 때 자연스럽게 로딩되는지 확인하고 있습니다.", "USER", LocalDateTime.now().minusMinutes(18), true),
                ChattingMessage(5, "user_2", "상대방", null, null, "알겠습니다. 제가 메시지를 몇 개 더 보내서 길이를 늘려 드릴게요.", "USER", LocalDateTime.now().minusMinutes(17), false),
                ChattingMessage(6, "user_1", "나", null, null, "감사합니다! 그럼 테스트가 훨씬 수월할 것 같아요.", "USER", LocalDateTime.now().minusMinutes(16), true),
                ChattingMessage(7, "system_1", "system", null, null, "상대방이 채팅방에 초대되었습니다.", "SYSTEM", LocalDateTime.now().minusMinutes(15), false),
                ChattingMessage(8, "user_2", "상대방", null, null, "스크롤 테스트 메시지 1", "USER", LocalDateTime.now().minusMinutes(14), false),
                ChattingMessage(9, "user_2", "상대방", null, null, "스크롤 테스트 메시지 2", "USER", LocalDateTime.now().minusMinutes(14), false),
                ChattingMessage(10, "user_1", "나", null, null, "잘 올라오네요. 조금만 더 보내주시겠어요?", "USER", LocalDateTime.now().minusMinutes(13), true),
                ChattingMessage(11, "user_2", "상대방", null, null, "스크롤 테스트 메시지 3", "USER", LocalDateTime.now().minusMinutes(12), false),
                ChattingMessage(12, "user_2", "상대방", null, null, "스크롤 테스트 메시지 4", "USER", LocalDateTime.now().minusMinutes(12), false),
                ChattingMessage(13, "user_2", "상대방", null, null, "스크롤 테스트 메시지 5", "USER", LocalDateTime.now().minusMinutes(12), false),
                ChattingMessage(14, "user_1", "나", null, null, "좋습니다. 이제 충분한 것 같아요.", "USER", LocalDateTime.now().minusMinutes(11), true),
                ChattingMessage(15, "user_2", "상대방", null, null, "필요하시면 더 말씀해주세요.", "USER", LocalDateTime.now().minusMinutes(10), false),
                ChattingMessage(16, "user_1", "나", null, null, "네, 감사합니다. 테스트 마저 진행해볼게요.", "USER", LocalDateTime.now().minusMinutes(9), true),
                ChattingMessage(17, "user_2", "상대방", null, null, "화이팅입니다.", "USER", LocalDateTime.now().minusMinutes(8), false),
                ChattingMessage(18, "user_1", "나", null, null, "ㅎㅎ", "USER", LocalDateTime.now().minusMinutes(7), true),
                ChattingMessage(19, "user_1", "나", null, null, "테스트가 거의 끝나갑니다.", "USER", LocalDateTime.now().minusMinutes(2), true),
                ChattingMessage(20, "user_2", "상대방", null, null, "수고하셨습니다!", "USER", LocalDateTime.now().minusMinutes(1), false)
            )
        }

        return BaseResult.Success(ChattingModel(hasNext = true, opponentActive = true, messages = messages))

        /* TODO: 실제 api 연결
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.getChatMessages(chatRoomId, prevChatId, size) },
            mapper = { dto: GetChattingMessagesDTO? ->
                dto?.toModel() ?: throw NoSuchElementException()
            },
        )
        */
    }

    override suspend fun getNewChattingMessages(
        chatRoomId: Long,
        lastMessageId: Long?
    ): BaseResult<ChattingModel> {
        // 더미 데이터
        val newMessages = listOf(
            ChattingMessage(
                chatId = System.currentTimeMillis(), // 매번 새로운 ID를 갖도록
                senderId = "user_2",
                nickname = "상대방",
                profileUrl = null,
                thumbnailImageUrl = null,
                content = "새로운 메시지! ${LocalDateTime.now()}",
                chatType = "USER",
                sentAt = LocalDateTime.now(),
                isMine = false
            )
        )
        // 실제로는 lastMessageId 이후의 메시지만 가져와야 함
        // 여기서는 항상 1개의 새 메시지를 반환
        val dummyModel = ChattingModel(
            hasNext = false,
            opponentActive = true,
            messages = newMessages
        )
        return BaseResult.Success(dummyModel)

        /* TODO: 실제 api 연결
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.getNewChatMessages(chatRoomId, lastMessageId) },
            mapper = { dto: GetNewChattingMessagesDTO? ->
                dto?.toModel() ?: throw NoSuchElementException()
            },
        )
        */
    }

    override suspend fun sendChattingMessage(
        chatRoomId: Long,
        content: String
    ): BaseResult<Long> {
        // 더미 데이터
        // 메시지 전송 성공 시, 서버에서 발급한 새로운 chatId를 반환한다고 가정
        val newChatId = System.currentTimeMillis()
        return BaseResult.Success(newChatId)

        /* TODO: 실제 api 연결
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.postChatMessage(chatRoomId, PostChatMessageRequest(content)) },
            mapper = { dto: PostMessageResponse? ->
                dto?.chatId ?: throw NoSuchElementException()
            },
        )
        */
    }
}