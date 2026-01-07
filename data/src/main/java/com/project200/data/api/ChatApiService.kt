package com.project200.data.api

import com.project200.data.dto.BaseResponse
import com.project200.data.dto.TicketResponse
import retrofit2.http.POST
import retrofit2.http.Path

interface ChatApiService {
    @POST("api/v1/chat-rooms/{chatroomId}/ticket")
    suspend fun getChatTicket(
        @Path("chatroomId") chatroomId: Long,
    ): BaseResponse<TicketResponse>
}
