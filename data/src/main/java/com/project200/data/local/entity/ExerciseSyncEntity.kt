package com.project200.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import java.time.LocalDate

/** 읽기 투영과 별도로 최신 의도와 전송 중 원문을 보존합니다 */
@Entity(
    tableName = "exercise_sync",
    primaryKeys = ["memberId", "localId"],
    indices = [Index(value = ["memberId", "serverId"])],
)
data class ExerciseSyncEntity(
    val memberId: String,
    val localId: String,
    val serverId: Long?,
    val operation: SyncState,
    val revision: Long?,
    val operationId: String?,
    val createRequestId: String?,
    val serverVersion: Long?,
    val confirmedDate: LocalDate?,
    val currentDate: LocalDate?,
    val recoveryState: ExerciseRecoveryState,
    val lastError: ExerciseSyncError? = null,
    val inFlightOperation: SyncState? = null,
    val inFlightRevision: Long? = null,
    val inFlightOperationId: String? = null,
    val inFlightRequestId: String? = null,
    val inFlightBody: String? = null,
    val inFlightBaseVersion: Long? = null,
)

enum class ExerciseRecoveryState {
    READY,
    RETRYABLE,
    RECOVERY_REQUIRED,
}

enum class ExerciseSyncError {
    LEGACY_PENDING,
    INCOMPLETE_DETAIL,
    CONTRADICTORY_STATE,
    MALFORMED_PICTURES,
    NETWORK_ERROR,
    CONFLICT,
}
