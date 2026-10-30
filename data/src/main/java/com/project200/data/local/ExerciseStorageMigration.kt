package com.project200.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.project200.data.local.entity.CachedPicture
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.IOException

/** 원본 투영과 사진 JSON은 수정하지 않고 새 저장 공간만 채웁니다 */
object ExerciseStorageMigration : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exercise_list_item ADD COLUMN serverVersion INTEGER")
        // v5에는 행별 조회 출처가 없으므로 실제 서버 목록을 수신할 때까지 확정 날짜는 null로 둡니다
        db.execSQL("ALTER TABLE exercise_list_item ADD COLUMN confirmedDate TEXT")
        db.execSQL("ALTER TABLE exercise_record_detail ADD COLUMN serverVersion INTEGER")
        db.execSQL("ALTER TABLE exercise_record_detail ADD COLUMN confirmedDate TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS exercise_sync (" +
                "memberId TEXT NOT NULL, localId TEXT NOT NULL, serverId INTEGER, operation TEXT NOT NULL, " +
                "revision INTEGER, operationId TEXT, createRequestId TEXT, serverVersion INTEGER, " +
                "confirmedDate TEXT, currentDate TEXT, recoveryState TEXT NOT NULL, lastError TEXT, " +
                "inFlightOperation TEXT, inFlightRevision INTEGER, inFlightOperationId TEXT, inFlightRequestId TEXT, " +
                "inFlightBody TEXT, inFlightBaseVersion INTEGER, PRIMARY KEY(memberId, localId))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_sync_memberId_serverId ON exercise_sync(memberId, serverId)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS exercise_image (" +
                "memberId TEXT NOT NULL, recordLocalId TEXT NOT NULL, localId TEXT NOT NULL, serverPictureId INTEGER, " +
                "localFilePath TEXT, serverUrl TEXT, state TEXT NOT NULL, requestId TEXT, sortOrder INTEGER NOT NULL, " +
                "PRIMARY KEY(memberId, recordLocalId, localId))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_exercise_image_memberId_recordLocalId_serverPictureId " +
                "ON exercise_image(memberId, recordLocalId, serverPictureId)",
        )
        db.execSQL(
            "INSERT INTO exercise_sync (memberId, localId, serverId, operation, currentDate, recoveryState, lastError) " +
                "SELECT k.memberId, k.localId, COALESCE(l.serverId, d.serverId), " +
                "CASE WHEN l.syncState != 'SYNCED' THEN l.syncState ELSE d.syncState END, " +
                "COALESCE(l.date, substr(d.startedAt, 1, 10)), 'RECOVERY_REQUIRED', " +
                "CASE WHEN l.localId IS NOT NULL AND d.localId IS NOT NULL " +
                "AND (l.syncState != d.syncState OR l.serverId IS NOT d.serverId) THEN 'CONTRADICTORY_STATE' " +
                "WHEN d.localId IS NULL THEN 'INCOMPLETE_DETAIL' ELSE 'LEGACY_PENDING' END " +
                "FROM (SELECT memberId, localId FROM exercise_list_item UNION SELECT memberId, localId FROM exercise_record_detail) k " +
                "LEFT JOIN exercise_list_item l ON l.memberId = k.memberId AND l.localId = k.localId " +
                "LEFT JOIN exercise_record_detail d ON d.memberId = k.memberId AND d.localId = k.localId " +
                "WHERE l.syncState != 'SYNCED' OR d.syncState != 'SYNCED'",
        )
        backfillPictures(db)
    }

    private fun backfillPictures(db: SupportSQLiteDatabase) {
        val adapter =
            Moshi.Builder().build().adapter<List<CachedPicture?>>(
                Types.newParameterizedType(List::class.java, CachedPicture::class.java),
            )
        db.query("SELECT memberId, localId, pictures FROM exercise_record_detail").use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.isNull(2)) continue
                val memberId = cursor.getString(0)
                val localId = cursor.getString(1)
                val pictures =
                    try {
                        adapter.fromJson(cursor.getString(2)).also { values ->
                            if (!values.isNullOrEmpty()) {
                                val ids = HashSet<Long>()
                                require(values.all { it != null && it.id > 0 && it.url.isNotBlank() && ids.add(it.id) })
                            }
                        }
                    } catch (_: IOException) {
                        markMalformedPictures(db, memberId, localId)
                        continue
                    } catch (_: JsonDataException) {
                        markMalformedPictures(db, memberId, localId)
                        continue
                    } catch (_: IllegalArgumentException) {
                        markMalformedPictures(db, memberId, localId)
                        continue
                    }
                pictures.orEmpty().forEachIndexed { index, value ->
                    val picture = requireNotNull(value)
                    db.execSQL(
                        "INSERT INTO exercise_image " +
                            "(memberId, recordLocalId, localId, serverPictureId, serverUrl, state, sortOrder) " +
                            "VALUES (?, ?, ?, ?, ?, 'SYNCED', ?)",
                        arrayOf(memberId, localId, "server:${picture.id}", picture.id, picture.url, index),
                    )
                }
            }
        }
    }

    private fun markMalformedPictures(
        db: SupportSQLiteDatabase,
        memberId: String,
        localId: String,
    ) {
        db.execSQL(
            "INSERT OR IGNORE INTO exercise_sync " +
                "(memberId, localId, serverId, operation, recoveryState, lastError) " +
                "SELECT memberId, localId, serverId, syncState, 'RECOVERY_REQUIRED', 'MALFORMED_PICTURES' " +
                "FROM exercise_record_detail WHERE memberId = ? AND localId = ?",
            arrayOf(memberId, localId),
        )
        db.execSQL(
            "UPDATE exercise_sync SET recoveryState = 'RECOVERY_REQUIRED', lastError = 'MALFORMED_PICTURES' " +
                "WHERE memberId = ? AND localId = ?",
            arrayOf(memberId, localId),
        )
    }
}
