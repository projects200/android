package com.project200.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 알 수 없는 과거 편집 시각과 기존 요청 원문은 그대로 둡니다. */
object ExerciseSyncUiMigration : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exercise_sync ADD COLUMN desiredEditedAt INTEGER")
        db.execSQL("ALTER TABLE exercise_sync ADD COLUMN inFlightEditedAt INTEGER")
        db.execSQL("ALTER TABLE exercise_record_detail ADD COLUMN editedAt INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS exercise_point_receipt " +
                "(memberId TEXT NOT NULL, createRequestId TEXT NOT NULL, recordLocalId TEXT NOT NULL, " +
                "serverId INTEGER NOT NULL, earnedPoints INTEGER NOT NULL, noticeConsumed INTEGER NOT NULL, " +
                "PRIMARY KEY(memberId, createRequestId))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_exercise_point_receipt_memberId_recordLocalId " +
                "ON exercise_point_receipt (memberId, recordLocalId)",
        )
    }
}
