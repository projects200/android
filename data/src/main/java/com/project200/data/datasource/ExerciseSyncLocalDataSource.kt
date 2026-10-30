package com.project200.data.datasource

import androidx.room.withTransaction
import com.project200.data.dto.ExercisePictureMappingDto
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.entity.ExerciseCountEntity
import com.project200.data.local.entity.ExerciseImageEntity
import com.project200.data.local.entity.ExerciseImageState
import com.project200.data.local.entity.ExerciseRecoveryState
import com.project200.data.local.entity.ExerciseSyncEntity
import com.project200.data.local.entity.ExerciseSyncError
import com.project200.data.local.entity.SyncState
import com.project200.data.mapper.toModel
import com.project200.data.mapper.toPostExerciseDTO
import com.squareup.moshi.Moshi
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** Repository의 상태/파일 락 아래에서만 호출합니다. 이 클래스는 네트워크를 호출하지 않습니다. */
class ExerciseSyncLocalDataSource
    @Inject
    constructor(
        private val database: UndabangDatabase,
        moshi: Moshi,
    ) {
        private val snapshots = moshi.adapter(ExerciseSyncSnapshot::class.java)
        private val journals get() = database.exerciseSyncDao()
        private val records get() = database.exerciseRecordDao()
        private val images get() = database.exerciseImageDao()

        suspend fun pendingIds(memberId: String): List<String> =
            (journals.getByMember(memberId).map { it.localId } + images.getPending(memberId).map { it.recordLocalId }).distinct()

        suspend fun hasRecovery(memberId: String): Boolean =
            journals.getByMember(memberId).any { it.recoveryState == ExerciseRecoveryState.RECOVERY_REQUIRED }

        suspend fun claim(memberId: String, localId: String): ExerciseSyncSnapshot? = database.withTransaction {
            val detail = records.getDetailByLocalId(memberId, localId)
            val item = records.getListItem(memberId, localId)
            val photos = images.getByRecord(memberId, localId)
            var journal = journals.get(memberId, localId)
            if (journal?.recoveryState == ExerciseRecoveryState.RECOVERY_REQUIRED) return@withTransaction null
            if (journal == null) {
                if (photos.none { it.state != ExerciseImageState.SYNCED }) return@withTransaction null
                journal = ExerciseSyncEntity(
                    memberId, localId, detail?.serverId ?: item?.serverId, SyncState.SYNCED,
                    1L, UUID.randomUUID().toString(), null, detail?.serverVersion ?: item?.serverVersion,
                    detail?.confirmedDate ?: item?.confirmedDate, item?.date ?: detail?.confirmedDate,
                    ExerciseRecoveryState.READY,
                )
                journals.saveSyncState(journal)
            }
            val current = requireNotNull(journal)
            try {
                require(item == null || item.serverId == current.serverId)
                require(detail == null || detail.serverId == current.serverId)
                require(current.operation != SyncState.CREATE_PENDING || current.serverId == null)
                require(current.operation != SyncState.UPDATE_PENDING || current.serverId != null)
                if (current.inFlightOperationId != null || current.inFlightBody != null || current.inFlightRequestId != null ||
                    current.inFlightOperation != null || current.inFlightRevision != null || current.inFlightBaseVersion != null
                ) {
                    val saved = requireNotNull(snapshots.fromJson(requireNotNull(current.inFlightBody)))
                    saved.validate()
                    require(saved.matches(current) && current.revision != null && current.revision >= saved.revision)
                    require(saved.serverId == current.serverId && saved.baseVersion == current.serverVersion)
                    require(saved.kind != ExerciseRequestKind.BODY || saved.operation != SyncState.CREATE_PENDING ||
                        saved.requestId == current.createRequestId)
                    if (saved.kind != ExerciseRequestKind.BODY) {
                        val image = requireNotNull(photos.singleOrNull { it.localId == saved.imageId && it.requestId == saved.imageRequestId })
                        require(image.serverPictureId == saved.pictureId)
                        if (saved.kind == ExerciseRequestKind.UPLOAD) {
                            require(image.localFilePath == saved.filePath &&
                                (image.state == ExerciseImageState.ADD_PENDING || image.state == ExerciseImageState.DELETE_PENDING))
                        } else {
                            require(image.state == ExerciseImageState.DELETE_PENDING)
                        }
                    }
                    // 서버 기록 삭제는 불확실한 사진까지 포함합니다. 그 밖에는 원래 요청을 먼저 해결합니다
                    if (current.operation != SyncState.DELETE_PENDING || current.serverId == null || saved.kind == ExerciseRequestKind.BODY) {
                        return@withTransaction saved
                    }
                }
                require(current.revision != null && current.revision > 0 && !current.operationId.isNullOrBlank())
                val unresolvedPhoto = photos.firstOrNull {
                    it.state == ExerciseImageState.ADD_PENDING || it.state == ExerciseImageState.DELETE_PENDING
                }
                val photo = unresolvedPhoto ?: photos.firstOrNull { it.state == ExerciseImageState.ADD_LOCAL }
                val sendBody = current.operation == SyncState.CREATE_PENDING || current.operation == SyncState.DELETE_PENDING ||
                    current.operation == SyncState.UPDATE_PENDING && unresolvedPhoto == null
                val snapshot = if (sendBody) {
                    require(current.operation != SyncState.CREATE_PENDING || current.serverId == null)
                    require(current.operation == SyncState.CREATE_PENDING || current.serverId != null)
                    require(current.operation != SyncState.UPDATE_PENDING || current.serverVersion != null)
                    ExerciseSyncSnapshot(
                        memberId, localId, ExerciseRequestKind.BODY, current.operation, current.revision,
                        current.operationId, if (current.operation == SyncState.CREATE_PENDING) requireNotNull(current.createRequestId) else current.operationId,
                        current.serverId, current.serverVersion, current.currentDate?.toString(), current.confirmedDate?.toString(),
                        if (current.operation == SyncState.DELETE_PENDING) null else requireNotNull(detail).toModel().toPostExerciseDTO(),
                    )
                } else {
                    if (photo == null) {
                        journals.deleteApplied(memberId, localId)
                        return@withTransaction null
                    }
                    require(current.serverId != null && current.serverVersion != null)
                    photoSnapshot(current, photo)
                }
                snapshot.validate()
                if (snapshot.kind == ExerciseRequestKind.UPLOAD) {
                    val image = photos.single { it.localId == snapshot.imageId }
                    if (image.state == ExerciseImageState.ADD_LOCAL) images.save(image.copy(state = ExerciseImageState.ADD_PENDING))
                }
                journals.saveSyncState(current.copy(
                    inFlightOperation = snapshot.operation, inFlightRevision = snapshot.revision,
                    inFlightOperationId = snapshot.operationId, inFlightRequestId = snapshot.requestId,
                    inFlightBody = snapshots.toJson(snapshot), inFlightBaseVersion = snapshot.baseVersion,
                ))
                snapshot
            } catch (_: IllegalArgumentException) {
                recover(current, ExerciseSyncError.CONTRADICTORY_STATE)
                null
            } catch (_: com.squareup.moshi.JsonDataException) {
                recover(current, ExerciseSyncError.CONTRADICTORY_STATE)
                null
            } catch (_: com.squareup.moshi.JsonEncodingException) {
                recover(current, ExerciseSyncError.CONTRADICTORY_STATE)
                null
            } catch (_: java.time.DateTimeException) {
                recover(current, ExerciseSyncError.CONTRADICTORY_STATE)
                null
            }
        }

        private fun photoSnapshot(journal: ExerciseSyncEntity, image: ExerciseImageEntity): ExerciseSyncSnapshot {
            val upload = image.serverPictureId == null
            require(!image.requestId.isNullOrBlank())
            require(!upload || image.state != ExerciseImageState.SYNCED)
            require(upload || image.state == ExerciseImageState.DELETE_PENDING)
            val file = if (upload) File(requireNotNull(image.localFilePath)) else null
            require(file == null || file.isFile && file.length() > 0)
            val mime = when (file?.extension?.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                else -> null
            }
            val requestId = if (upload) image.requestId else UUID.nameUUIDFromBytes(
                "exercise-photo-delete:${journal.memberId}:${journal.localId}:${image.localId}:${image.requestId}".toByteArray(Charsets.UTF_8),
            ).toString()
            return ExerciseSyncSnapshot(
                journal.memberId, journal.localId,
                if (upload) ExerciseRequestKind.UPLOAD else ExerciseRequestKind.DELETE_PHOTO,
                SyncState.SYNCED, requireNotNull(journal.revision), requireNotNull(journal.operationId), requestId,
                journal.serverId, journal.serverVersion, journal.currentDate?.toString(), journal.confirmedDate?.toString(),
                imageId = image.localId, imageRequestId = image.requestId, pictureId = image.serverPictureId,
                filePath = file?.absolutePath, filename = file?.name, mimeType = mime,
                byteLength = file?.length(), sha256 = file?.let { hash(it) },
            )
        }

        suspend fun fail(snapshot: ExerciseSyncSnapshot, retryable: Boolean, error: ExerciseSyncError): Boolean = database.withTransaction {
            val current = matched(snapshot) ?: return@withTransaction false
            if (current.operation == SyncState.DELETE_PENDING && current.serverId != null && snapshot.operation != SyncState.DELETE_PENDING) {
                // 원하는 최종 상태가 전체 삭제이면 미확정 수정/사진은 서버 삭제가 함께 정리합니다
                journals.saveSyncState(clearSnapshot(current).copy(recoveryState = ExerciseRecoveryState.READY, lastError = null))
                return@withTransaction true
            }
            journals.saveSyncState(current.copy(
                recoveryState = if (retryable) ExerciseRecoveryState.RETRYABLE else ExerciseRecoveryState.RECOVERY_REQUIRED,
                lastError = error,
            ))
            false
        }

        suspend fun applyBody(snapshot: ExerciseSyncSnapshot, serverId: Long, version: Long, date: LocalDate?): Boolean =
            database.withTransaction {
                val current = matched(snapshot) ?: return@withTransaction false
                require(snapshot.kind == ExerciseRequestKind.BODY && serverId > 0 && version >= 0)
                require(snapshot.serverId == null || snapshot.serverId == serverId)
                if (snapshot.operation == SyncState.DELETE_PENDING) {
                    moveCount(snapshot.memberId, snapshot.previousDate?.let(LocalDate::parse), null)
                    images.getByRecord(snapshot.memberId, snapshot.localId).forEach {
                        images.deleteMatching(it.memberId, it.recordLocalId, it.localId, it.requestId)
                    }
                    records.deleteConfirmedList(snapshot.memberId, snapshot.localId)
                    records.deleteConfirmedDetail(snapshot.memberId, snapshot.localId)
                    journals.deleteApplied(snapshot.memberId, snapshot.localId)
                    return@withTransaction true
                }
                require(date != null)
                moveCount(snapshot.memberId, snapshot.previousDate?.let(LocalDate::parse), date)
                val operation = when {
                    current.operation == SyncState.DELETE_PENDING -> SyncState.DELETE_PENDING
                    current.revision == snapshot.revision && current.operationId == snapshot.operationId -> SyncState.SYNCED
                    else -> SyncState.UPDATE_PENDING
                }
                val next = clearSnapshot(current).copy(
                    serverId = serverId, serverVersion = version, confirmedDate = date, operation = operation,
                    recoveryState = ExerciseRecoveryState.READY, lastError = null,
                )
                saveProjection(next)
                if (operation == SyncState.SYNCED) journals.deleteApplied(next.memberId, next.localId) else journals.saveSyncState(next)
                true
            }

        suspend fun applyPhoto(snapshot: ExerciseSyncSnapshot, version: Long, mapping: ExercisePictureMappingDto?): Boolean =
            database.withTransaction {
                val current = matched(snapshot) ?: return@withTransaction false
                val image = images.getByRecord(snapshot.memberId, snapshot.localId).singleOrNull {
                    it.localId == snapshot.imageId && it.requestId == snapshot.imageRequestId
                } ?: return@withTransaction false
                if (snapshot.kind == ExerciseRequestKind.UPLOAD) {
                    require(mapping != null && mapping.clientImageId == image.localId && mapping.pictureId > 0 && mapping.pictureUrl.isNotBlank())
                    images.save(image.copy(
                        serverPictureId = mapping.pictureId, serverUrl = mapping.pictureUrl,
                        state = if (image.state == ExerciseImageState.DELETE_PENDING) ExerciseImageState.DELETE_PENDING else ExerciseImageState.SYNCED,
                    ))
                } else {
                    require(image.serverPictureId == snapshot.pictureId && image.state == ExerciseImageState.DELETE_PENDING)
                    images.deleteMatching(image.memberId, image.recordLocalId, image.localId, image.requestId)
                }
                // 마지막 사진의 행을 지운 뒤 구 목록 썸네일로 되돌아가지 않게 합니다
                records.getListItem(snapshot.memberId, snapshot.localId)?.let { item ->
                    val remaining = images.getByRecord(snapshot.memberId, snapshot.localId)
                    records.upsertListItems(listOf(item.copy(thumbnailUrls = remaining.mapNotNull { it.toModel()?.source })))
                }
                val expected = requireNotNull(snapshot.baseVersion)
                val consistent = version == expected + 1 || snapshot.kind == ExerciseRequestKind.DELETE_PHOTO && version == expected
                if (!consistent) {
                    // 사진 매핑은 증명됐지만 타 기기의 본문 변경 가능성은 PATCH 기준으로 채택하지 않습니다
                    recover(current, ExerciseSyncError.CONFLICT)
                } else {
                    val next = clearSnapshot(current).copy(serverVersion = version, recoveryState = ExerciseRecoveryState.READY, lastError = null)
                    saveProjection(next)
                    if (next.operation == SyncState.SYNCED) journals.deleteApplied(next.memberId, next.localId) else journals.saveSyncState(next)
                }
                true
            }

        private suspend fun matched(snapshot: ExerciseSyncSnapshot): ExerciseSyncEntity? =
            journals.get(snapshot.memberId, snapshot.localId)?.takeIf {
                snapshot.matches(it) && it.serverVersion == snapshot.baseVersion &&
                    it.inFlightBody?.let { json -> snapshots.fromJson(json) == snapshot } == true
            }

        private suspend fun recover(journal: ExerciseSyncEntity, error: ExerciseSyncError) {
            journals.saveSyncState(journal.copy(recoveryState = ExerciseRecoveryState.RECOVERY_REQUIRED, lastError = error))
        }

        private fun clearSnapshot(journal: ExerciseSyncEntity): ExerciseSyncEntity = journal.copy(
            inFlightOperation = null, inFlightRevision = null, inFlightOperationId = null,
            inFlightRequestId = null, inFlightBody = null, inFlightBaseVersion = null,
        )

        private suspend fun saveProjection(journal: ExerciseSyncEntity) {
            records.getListItem(journal.memberId, journal.localId)?.let {
                records.upsertListItems(listOf(it.copy(
                    serverId = journal.serverId, serverVersion = journal.serverVersion,
                    confirmedDate = journal.confirmedDate, syncState = journal.operation,
                    date = if (journal.operation == SyncState.SYNCED) journal.confirmedDate ?: it.date else it.date,
                )))
            }
            records.getDetailByLocalId(journal.memberId, journal.localId)?.let {
                records.upsertDetail(it.copy(
                    serverId = journal.serverId, serverVersion = journal.serverVersion,
                    confirmedDate = journal.confirmedDate, syncState = journal.operation,
                ))
            }
        }

        private suspend fun moveCount(memberId: String, previous: LocalDate?, confirmed: LocalDate?) {
            if (previous == confirmed) return
            for ((date, delta) in listOf(previous to -1, confirmed to 1)) {
                if (date == null) continue
                val baseline = database.exerciseCountDao().getRange(memberId, date, date).singleOrNull()?.count ?: 0
                database.exerciseCountDao().upsertAll(listOf(ExerciseCountEntity(memberId, date, (baseline + delta).coerceAtLeast(0))))
            }
        }

        companion object {
            fun hash(file: File): String {
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                val bytes = digest.digest()
                val hex = "0123456789abcdef"
                val result = CharArray(bytes.size * 2)
                bytes.forEachIndexed { index, byte ->
                    val value = byte.toInt() and 0xff
                    result[index * 2] = hex[value ushr 4]
                    result[index * 2 + 1] = hex[value and 0xf]
                }
                return String(result)
            }
        }
    }
