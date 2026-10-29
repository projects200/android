package com.project200.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.project200.data.local.entity.CachedPicture
import com.project200.data.local.entity.ExerciseImageEntity
import com.project200.data.local.entity.ExerciseImageState
import java.util.UUID

@Dao
abstract class ExerciseImageDao {
    @Query("SELECT * FROM exercise_image WHERE memberId = :memberId AND recordLocalId = :recordLocalId ORDER BY sortOrder, localId")
    abstract suspend fun getByRecord(
        memberId: String,
        recordLocalId: String,
    ): List<ExerciseImageEntity>

    @Query("SELECT * FROM exercise_image WHERE memberId = :memberId AND state != 'SYNCED' ORDER BY recordLocalId, sortOrder")
    abstract suspend fun getPending(memberId: String): List<ExerciseImageEntity>

    @Query("SELECT DISTINCT memberId FROM exercise_image WHERE state = 'SYNCED' AND localFilePath IS NULL AND requestId IS NULL")
    abstract suspend fun getRemoteCacheMembers(): List<String>

    @Upsert
    protected abstract suspend fun upsert(entity: ExerciseImageEntity)

    @Transaction
    open suspend fun save(entity: ExerciseImageEntity) {
        require(entity.state == ExerciseImageState.SYNCED || !entity.requestId.isNullOrBlank()) { "IMAGE_REQUEST_REQUIRED" }
        val previous = getByRecord(entity.memberId, entity.recordLocalId).find { it.localId == entity.localId }
        require(previous?.requestId == null || previous.requestId == entity.requestId) { "IMAGE_REQUEST_IMMUTABLE" }
        require(previous?.serverPictureId == null || previous.serverPictureId == entity.serverPictureId) { "PICTURE_ID_IMMUTABLE" }
        upsert(entity)
    }

    /** 서버 사진 ID로만 연결하며 로컬 파일·대기 작업은 서버 캐시로 교체하지 않습니다 */
    @Transaction
    open suspend fun refreshSynced(
        memberId: String,
        recordLocalId: String,
        pictures: List<CachedPicture>?,
    ) {
        val existing = getByRecord(memberId, recordLocalId)
        val incomingIds = pictures.orEmpty().map { it.id }.toSet()
        existing.filter { it.isRemoteCache() && it.serverPictureId !in incomingIds }.forEach {
            deleteRemoteCache(memberId, recordLocalId, it.localId)
        }
        pictures.orEmpty().forEachIndexed { index, picture ->
            val previous = existing.find { it.serverPictureId == picture.id }
            if (previous == null) {
                // 같은 기록의 서버 ID를 써서 다시 조회해도 로컬 식별자를 유지합니다
                val preferredId = "server:${picture.id}"
                val localId = if (existing.any { it.localId == preferredId }) UUID.randomUUID().toString() else preferredId
                upsert(
                    ExerciseImageEntity(
                        memberId,
                        recordLocalId,
                        localId,
                        picture.id,
                        null,
                        picture.url,
                        ExerciseImageState.SYNCED,
                        null,
                        index,
                    ),
                )
            } else if (previous.state == ExerciseImageState.SYNCED) {
                upsert(previous.copy(serverUrl = picture.url, sortOrder = index))
            }
        }
    }

    @Query(
        "DELETE FROM exercise_image WHERE memberId = :memberId AND recordLocalId = :recordLocalId AND localId = :localId " +
            "AND state = 'SYNCED' AND localFilePath IS NULL AND requestId IS NULL",
    )
    abstract suspend fun deleteRemoteCache(
        memberId: String,
        recordLocalId: String,
        localId: String,
    ): Int

    @Query(
        "DELETE FROM exercise_image WHERE memberId = :memberId AND recordLocalId = :recordLocalId AND localId = :localId " +
            "AND requestId IS :requestId",
    )
    abstract suspend fun deleteMatching(
        memberId: String,
        recordLocalId: String,
        localId: String,
        requestId: String?,
    ): Int

    /** 투영과 저널이 모두 없는 재조회 가능한 메타데이터만 정리합니다 */
    @Query(
        "DELETE FROM exercise_image WHERE memberId = :memberId AND state = 'SYNCED' AND localFilePath IS NULL AND requestId IS NULL " +
            "AND NOT EXISTS (SELECT 1 FROM exercise_record_detail d " +
            "WHERE d.memberId = exercise_image.memberId AND d.localId = exercise_image.recordLocalId) " +
            "AND NOT EXISTS (SELECT 1 FROM exercise_list_item l " +
            "WHERE l.memberId = exercise_image.memberId AND l.localId = exercise_image.recordLocalId) " +
            "AND NOT EXISTS (SELECT 1 FROM exercise_sync s " +
            "WHERE s.memberId = exercise_image.memberId AND s.localId = exercise_image.recordLocalId)",
    )
    abstract suspend fun deleteOrphanedRemoteCache(memberId: String)

    private fun ExerciseImageEntity.isRemoteCache(): Boolean =
        state == ExerciseImageState.SYNCED && localFilePath == null && requestId == null
}
