package com.project200.data.mapper

import com.project200.data.local.entity.CachedPicture
import com.project200.data.local.entity.ExerciseCountEntity
import com.project200.data.local.entity.ExerciseListItemEntity
import com.project200.data.local.entity.ExerciseRecordDetailEntity
import com.project200.data.local.entity.SyncState
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseRecordPicture
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
        serverId = recordId,
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

fun ExerciseListItemEntity.toModel(): ExerciseListItem {
    return ExerciseListItem(
        recordId = serverId ?: NO_SERVER_ID,
        title = title,
        type = personalType,
        startTime = startedAt,
        endTime = endedAt,
        imageUrl = thumbnailUrls,
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
        pictures = pictures?.map { CachedPicture(it.id, it.url) },
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
        pictures = pictures?.map { ExerciseRecordPicture(it.id, it.url) },
    )
}

/**
 * 아직 서버 ID가 없는 기록을 도메인 모델로 옮길 때 쓰는 값입니다.
 * 화면이 로컬 ID로 기록을 식별하게 바뀌면 사라집니다 (#584)
 */
private const val NO_SERVER_ID = -1L
