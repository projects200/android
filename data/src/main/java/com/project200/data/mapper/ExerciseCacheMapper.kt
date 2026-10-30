package com.project200.data.mapper

import com.project200.data.local.entity.CachedPicture
import com.project200.data.local.entity.ExerciseCountEntity
import com.project200.data.local.entity.ExerciseImageEntity
import com.project200.data.local.entity.ExerciseImageState
import com.project200.data.local.entity.ExerciseListItemEntity
import com.project200.data.local.entity.ExerciseRecordDetailEntity
import com.project200.data.local.entity.SyncState
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.ExerciseImageSyncStatus
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseRecordPicture
import java.io.File
import java.time.LocalDate

fun ExerciseCount.toEntity(memberId: String): ExerciseCountEntity {
    return ExerciseCountEntity(
        memberId = memberId,
        date = date,
        count = count,
    )
}

fun ExerciseCountEntity.toModel(): ExerciseCount {
    return ExerciseCount(
        date = date,
        count = count,
    )
}

/** 서버에서 받은 목록 한 줄입니다. 이미 캐시에 있던 기록이면 localId를 이어 씁니다 */
fun ExerciseListItem.toSyncedEntity(
    memberId: String,
    localId: String,
    date: LocalDate,
    sortOrder: Int,
): ExerciseListItemEntity {
    return ExerciseListItemEntity(
        memberId = memberId,
        localId = localId,
        serverId = recordId.toLong(),
        syncState = SyncState.SYNCED,
        date = date,
        sortOrder = sortOrder,
        title = title,
        personalType = type,
        startedAt = startTime,
        endedAt = endTime,
        thumbnailUrls = imageUrl,
    )
}

/** 화면과 내비게이션은 서버 ID 유무와 관계없이 같은 localId를 씁니다. */
fun ExerciseListItemEntity.toModel(): ExerciseListItem {
    return ExerciseListItem(
        recordId = localId,
        title = title,
        type = personalType,
        startTime = startedAt,
        endTime = endedAt,
        imageUrl = thumbnailUrls,
        syncPending = syncState != SyncState.SYNCED,
    )
}

fun ExerciseRecord.toSyncedEntity(
    memberId: String,
    localId: String,
    serverId: Long,
): ExerciseRecordDetailEntity {
    return ExerciseRecordDetailEntity(
        memberId = memberId,
        localId = localId,
        serverId = serverId,
        syncState = SyncState.SYNCED,
        title = title,
        detail = detail,
        personalType = personalType,
        startedAt = startedAt,
        endedAt = endedAt,
        location = location,
        pictures = pictures?.mapNotNull { picture -> picture.serverId?.let { CachedPicture(it, picture.source) } },
    )
}

fun ExerciseRecordDetailEntity.toModel(): ExerciseRecord {
    return ExerciseRecord(
        title = title,
        detail = detail,
        personalType = personalType,
        startedAt = startedAt,
        endedAt = endedAt,
        location = location,
        pictures = pictures?.map { ExerciseRecordPicture("server:${it.id}", it.id, it.url, ExerciseImageSyncStatus.SYNCED) },
        syncPending = syncState != SyncState.SYNCED,
        canModify = syncState != SyncState.DELETE_PENDING,
    )
}

fun ExerciseImageEntity.toModel(): ExerciseRecordPicture? {
    if (state == ExerciseImageState.DELETE_PENDING) return null
    val source = localFilePath?.let { File(it).toURI().toString() } ?: serverUrl ?: return null
    return ExerciseRecordPicture(
        localId = localId,
        serverId = serverPictureId,
        source = source,
        syncStatus = if (state == ExerciseImageState.SYNCED) ExerciseImageSyncStatus.SYNCED else ExerciseImageSyncStatus.PENDING,
    )
}
