package com.project200.data.local

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import com.project200.data.local.entity.CachedPicture
import com.project200.data.local.entity.CachedTimerStep
import com.project200.data.local.entity.SyncState
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * SQLite가 아는 타입은 INTEGER, REAL, TEXT, BLOB 넷뿐이라 나머지는 여기서 문자열로 바꿉니다.
 *
 * 날짜는 ISO 문자열로 담습니다. 사전순 비교가 곧 시간순 비교라서
 * BETWEEN과 ORDER BY를 컬럼에 그대로 걸 수 있습니다
 *
 * 기존 상세 사진 JSON은 호환성을 위해 유지하며 순서와 null·빈 목록을 구분합니다
 * 사진 전송 상태와 앱 소유 파일 경로는 별도 메타데이터 테이블에 보존합니다
 */
@ProvidedTypeConverter
class UndabangTypeConverters(moshi: Moshi) {
    private val stringListAdapter =
        moshi.adapter<List<String>>(
            Types.newParameterizedType(List::class.java, String::class.java),
        )

    private val pictureListAdapter =
        moshi.adapter<List<CachedPicture>>(
            Types.newParameterizedType(List::class.java, CachedPicture::class.java),
        )

    private val timerStepListAdapter =
        moshi.adapter<List<CachedTimerStep>>(
            Types.newParameterizedType(List::class.java, CachedTimerStep::class.java),
        )

    @TypeConverter
    fun fromSyncState(value: SyncState?): String? = value?.name

    @TypeConverter
    fun toSyncState(value: String?): SyncState? = value?.let(SyncState::valueOf)

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)

    @TypeConverter
    fun fromStringList(value: List<String>?): String? = value?.let(stringListAdapter::toJson)

    @TypeConverter
    fun toStringList(value: String?): List<String>? = value?.let(stringListAdapter::fromJson)

    @TypeConverter
    fun fromPictureList(value: List<CachedPicture>?): String? = value?.let(pictureListAdapter::toJson)

    @TypeConverter
    fun toPictureList(value: String?): List<CachedPicture>? = value?.let(pictureListAdapter::fromJson)

    @TypeConverter
    fun fromTimerStepList(value: List<CachedTimerStep>?): String? = value?.let(timerStepListAdapter::toJson)

    @TypeConverter
    fun toTimerStepList(value: String?): List<CachedTimerStep>? = value?.let(timerStepListAdapter::fromJson)
}
