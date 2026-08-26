package com.project200.data.local

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import com.project200.data.local.entity.CachedPicture
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
 * 사진 목록은 JSON 문자열입니다. 사진을 따로 조회할 일이 없고 항상 기록과 함께 읽히며,
 * 리스트 순서가 그대로 보존됩니다
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
}
