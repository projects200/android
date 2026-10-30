package com.project200.data.datasource

import com.project200.data.dto.PatchExerciseRequestDto
import com.project200.data.dto.PostExerciseRequestDto
import com.project200.data.local.entity.ExerciseSyncEntity
import com.project200.data.local.entity.SyncState
import com.squareup.moshi.JsonClass
import java.time.LocalDate
import java.util.UUID

/** 요청과 집계 기준을 함께 고정합니다. 재편집은 이 snapshot을 변경하지 않습니다. */
@JsonClass(generateAdapter = true)
data class ExerciseSyncSnapshot(
    val memberId: String,
    val localId: String,
    val kind: ExerciseRequestKind,
    val operation: SyncState,
    val revision: Long,
    val operationId: String,
    val requestId: String,
    val serverId: Long?,
    val baseVersion: Long?,
    val submittedDate: String?,
    val previousDate: String?,
    val body: PostExerciseRequestDto? = null,
    val imageId: String? = null,
    val imageRequestId: String? = null,
    val pictureId: Long? = null,
    val filePath: String? = null,
    val filename: String? = null,
    val mimeType: String? = null,
    val byteLength: Long? = null,
    val sha256: String? = null,
) {
    fun matches(journal: ExerciseSyncEntity): Boolean =
        memberId == journal.memberId && localId == journal.localId &&
            operation == journal.inFlightOperation && revision == journal.inFlightRevision &&
            operationId == journal.inFlightOperationId && requestId == journal.inFlightRequestId &&
            baseVersion == journal.inFlightBaseVersion

    fun validate() {
        require(revision > 0 && memberId.isNotBlank() && localId.isNotBlank())
        UUID.fromString(operationId)
        UUID.fromString(requestId)
        submittedDate?.let { LocalDate.parse(it) }
        previousDate?.let { LocalDate.parse(it) }
        require(baseVersion == null || baseVersion >= 0)
        when (kind) {
            ExerciseRequestKind.BODY -> {
                require(operation != SyncState.SYNCED)
                require(operation == SyncState.CREATE_PENDING || serverId != null && serverId > 0)
                require(operation != SyncState.CREATE_PENDING || serverId == null)
                require(operation != SyncState.CREATE_PENDING || baseVersion == null && previousDate == null)
                if (operation != SyncState.DELETE_PENDING) {
                    require(body != null && submittedDate != null && body.createRequestId == null)
                    require(body.exerciseTitle != null && body.exerciseDetail != null && body.exercisePersonalType != null)
                    require(body.exerciseLocation != null && body.exerciseStartedAt != null && body.exerciseEndedAt != null)
                    java.time.LocalDateTime.parse(body.exerciseStartedAt)
                    java.time.LocalDateTime.parse(body.exerciseEndedAt)
                }
                require(operation == SyncState.CREATE_PENDING || baseVersion != null)
                require(operation != SyncState.UPDATE_PENDING || previousDate != null)
            }
            ExerciseRequestKind.UPLOAD, ExerciseRequestKind.DELETE_PHOTO -> {
                require(operation == SyncState.SYNCED && serverId != null && serverId > 0 && baseVersion != null)
                require(!imageId.isNullOrBlank() && !imageRequestId.isNullOrBlank())
                UUID.fromString(imageRequestId)
                if (kind == ExerciseRequestKind.UPLOAD) {
                    UUID.fromString(imageId)
                    require(requestId == imageRequestId && !filePath.isNullOrBlank() && !filename.isNullOrBlank())
                    require(mimeType?.startsWith("image/") == true)
                    require(byteLength != null && byteLength > 0 && sha256?.length == 64)
                } else {
                    require(pictureId != null && pictureId > 0 && requestId != imageRequestId)
                }
            }
        }
    }

    fun createRequest(): PostExerciseRequestDto = requireNotNull(body).copy(createRequestId = requestId)

    fun editRequest(): PatchExerciseRequestDto = requireNotNull(body).let {
        PatchExerciseRequestDto(
            it.exerciseTitle, it.exercisePersonalType, it.exerciseLocation, it.exerciseDetail,
            it.exerciseStartedAt, it.exerciseEndedAt, requestId, requireNotNull(baseVersion),
        )
    }
}

enum class ExerciseRequestKind { BODY, UPLOAD, DELETE_PHOTO }
