package com.project200.domain.usecase

import com.project200.domain.model.ChatSocketConnection
import com.project200.domain.repository.ChatSocketRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveChatConnectionUseCase
    @Inject
    constructor(
        private val chatSocketRepository: ChatSocketRepository,
    ) {
        operator fun invoke(): StateFlow<ChatSocketConnection> = chatSocketRepository.connection
    }
