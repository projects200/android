package com.project200.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.project200.data.local.entity.ExerciseRecoveryState
import com.project200.data.local.entity.ExerciseSyncEntity
import com.project200.data.local.entity.SyncState

@Dao
abstract class ExerciseSyncDao {
    @Query("SELECT * FROM exercise_sync WHERE memberId = :memberId AND localId = :localId")
    abstract suspend fun get(
        memberId: String,
        localId: String,
    ): ExerciseSyncEntity?

    @Query("SELECT localId FROM exercise_sync WHERE memberId = :memberId AND serverId = :serverId")
    abstract suspend fun findLocalId(
        memberId: String,
        serverId: Long,
    ): String?

    @Query("SELECT * FROM exercise_sync WHERE memberId = :memberId ORDER BY localId")
    abstract suspend fun getByMember(memberId: String): List<ExerciseSyncEntity>

    /** 복구 필요 행은 제외합니다. 기존 in-flight가 있으면 그 원문과 키로만 재시도합니다 */
    @Query(
        "SELECT * FROM exercise_sync WHERE memberId = :memberId AND operation != 'SYNCED' " +
            "AND recoveryState != 'RECOVERY_REQUIRED' AND revision IS NOT NULL AND operationId IS NOT NULL " +
            "AND currentDate IS NOT NULL AND (operation != 'CREATE_PENDING' OR createRequestId IS NOT NULL) ORDER BY localId",
    )
    abstract suspend fun getSendable(memberId: String): List<ExerciseSyncEntity>

    @Upsert
    protected abstract suspend fun upsert(entity: ExerciseSyncEntity)

    /** 최신 의도를 저장할 때 기존 전송 원문과 생성 키는 변경하지 않습니다 */
    @Transaction
    open suspend fun saveDesired(entity: ExerciseSyncEntity) {
        require(entity.operation != SyncState.SYNCED) { "PENDING_OPERATION_REQUIRED" }
        val previous = get(entity.memberId, entity.localId)
        if (entity.recoveryState != ExerciseRecoveryState.RECOVERY_REQUIRED) {
            require(entity.revision != null && entity.revision > 0 && !entity.operationId.isNullOrBlank()) { "INCOMPLETE_OPERATION" }
            require(entity.currentDate != null) { "CURRENT_DATE_REQUIRED" }
            require(entity.operation != SyncState.CREATE_PENDING || !entity.createRequestId.isNullOrBlank()) { "CREATE_KEY_REQUIRED" }
        }
        if (previous?.revision != null) {
            require(entity.revision != null && entity.revision >= previous.revision) { "STALE_REVISION" }
            require(entity.revision != previous.revision || entity.operationId == previous.operationId) { "REVISION_ID_MISMATCH" }
        }
        require(previous?.createRequestId == null || entity.createRequestId == previous.createRequestId) { "CREATE_KEY_IMMUTABLE" }
        require(previous?.serverId == null || entity.serverId == previous.serverId) { "SERVER_ID_IMMUTABLE" }
        upsert(
            entity.copy(
                inFlightOperation = previous?.inFlightOperation,
                inFlightRevision = previous?.inFlightRevision,
                inFlightOperationId = previous?.inFlightOperationId,
                inFlightRequestId = previous?.inFlightRequestId,
                inFlightBody = previous?.inFlightBody,
                inFlightBaseVersion = previous?.inFlightBaseVersion,
            ),
        )
    }

    /** DB 안에서만 claim하고 네트워크 요청은 이 처리 밖에서 실행합니다 */
    @Transaction
    open suspend fun claim(
        memberId: String,
        localId: String,
        revision: Long,
        operationId: String,
        requestId: String,
        body: String,
        baseVersion: Long?,
    ): Boolean {
        require(requestId.isNotBlank() && body.isNotBlank()) { "SNAPSHOT_REQUIRED" }
        val current = get(memberId, localId) ?: return false
        if (current.operation == SyncState.CREATE_PENDING && requestId != current.createRequestId) return false
        return claimMatching(memberId, localId, revision, operationId, requestId, body, baseVersion) == 1
    }

    @Query(
        "UPDATE exercise_sync SET inFlightOperation = operation, inFlightRevision = revision, " +
            "inFlightOperationId = operationId, inFlightRequestId = :requestId, inFlightBody = :body, inFlightBaseVersion = :baseVersion " +
            "WHERE memberId = :memberId AND localId = :localId AND revision = :revision AND operationId = :operationId " +
            "AND operation != 'SYNCED' AND recoveryState != 'RECOVERY_REQUIRED' AND inFlightOperationId IS NULL",
    )
    protected abstract suspend fun claimMatching(
        memberId: String,
        localId: String,
        revision: Long,
        operationId: String,
        requestId: String,
        body: String,
        baseVersion: Long?,
    ): Int

    /** 일치한 전송 원문만 해제합니다. 최신 의도와 읽기 투영은 완료 처리하지 않습니다 */
    @Query(
        "UPDATE exercise_sync SET inFlightOperation = NULL, inFlightRevision = NULL, inFlightOperationId = NULL, " +
            "inFlightRequestId = NULL, inFlightBody = NULL, inFlightBaseVersion = NULL " +
            "WHERE memberId = :memberId AND localId = :localId AND inFlightOperationId = :operationId " +
            "AND inFlightRevision = :revision AND inFlightRequestId = :requestId",
    )
    abstract suspend fun clearMatchingInFlight(
        memberId: String,
        localId: String,
        operationId: String,
        revision: Long,
        requestId: String,
    ): Int

    @Query(
        "DELETE FROM exercise_sync WHERE memberId = :memberId AND localId = :localId " +
            "AND operationId IS :operationId AND revision IS :revision AND inFlightOperationId IS NULL " +
            "AND inFlightOperation IS NULL AND inFlightRevision IS NULL AND inFlightBody IS NULL " +
            "AND inFlightRequestId IS NULL AND inFlightBaseVersion IS NULL",
    )
    abstract suspend fun deleteMatching(
        memberId: String,
        localId: String,
        operationId: String?,
        revision: Long?,
    ): Int
}
