package com.project200.domain.repository

import com.project200.domain.model.BaseResult

interface FcmRepository {
    suspend fun sendFcmToken(): BaseResult<Unit>
}
