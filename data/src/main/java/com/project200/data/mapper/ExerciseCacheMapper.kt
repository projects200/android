package com.project200.data.mapper

import com.project200.data.local.entity.CachedPicture
import com.project200.data.local.entity.ExerciseCountEntity
import com.project200.data.local.entity.ExerciseListItemEntity
import com.project200.data.local.entity.ExerciseRecordDetailEntity
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

fun ExerciseListItem.toEntity(
    memberId: String,
    date: LocalDate,
    sortOrder: Int,
): ExerciseListItemEntity {
    return ExerciseListItemEntity(
        memberId = memberId,
        recordId = recordId,
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
        recordId = recordId,
        title = title,
        type = personalType,
        startTime = startedAt,
        endTime = endedAt,
        imageUrl = thumbnailUrls,
    )
}

fun ExerciseRecord.toEntity(
    memberId: String,
    recordId: Long,
): ExerciseRecordDetailEntity {
    return ExerciseRecordDetailEntity(
        memberId = memberId,
        recordId = recordId,
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
