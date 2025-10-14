package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.domain.model.BaseResult
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
    }
