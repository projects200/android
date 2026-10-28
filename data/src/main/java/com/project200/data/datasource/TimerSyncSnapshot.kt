package com.project200.data.datasource

import com.project200.data.local.entity.CachedTimerStep
import com.project200.data.local.entity.SyncState

sealed interface TimerSyncSnapshot {
    val memberId: String
    val localId: String
    val serverId: Long?
    val state: SyncState
    val editId: String?
    val editedAt: Long

    data class Simple(
        override val memberId: String,
        override val localId: String,
        override val serverId: Long?,
        override val state: SyncState,
        override val editId: String?,
        override val editedAt: Long,
        val createRequestId: String,
        val time: Int,
    ) : TimerSyncSnapshot

    data class Custom(
        override val memberId: String,
        override val localId: String,
        override val serverId: Long?,
        override val state: SyncState,
        override val editId: String?,
        override val editedAt: Long,
        val createRequestId: String,
        val name: String,
        val steps: List<CachedTimerStep>,
    ) : TimerSyncSnapshot
}
