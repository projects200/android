package com.project200.domain.repository

import com.project200.domain.model.UpdateInfo

interface AppUpdateRepository {
    suspend fun getUpdateInfo(): Result<UpdateInfo>

    fun getCurrentVersionCode(): Long
}