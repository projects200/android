package com.project200.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.project200.data.local.dao.ExerciseCountDao
import com.project200.data.local.dao.ExerciseRecordDao
import com.project200.data.local.entity.ExerciseCountEntity
import com.project200.data.local.entity.ExerciseListItemEntity
import com.project200.data.local.entity.ExerciseRecordDetailEntity

/**
 * 오프라인 캐시용 로컬 데이터베이스입니다.
 *
 * 모든 테이블은 memberId 컬럼을 두고 조회에도 계정 조건을 겁니다
 * 계정이 바뀌면 이전 사용자의 행이 보이면 안 되기 때문입니다
 */
@Database(
    entities = [
        ExerciseCountEntity::class,
        ExerciseListItemEntity::class,
        ExerciseRecordDetailEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(UndabangTypeConverters::class)
abstract class UndabangDatabase : RoomDatabase() {
    abstract fun exerciseCountDao(): ExerciseCountDao

    abstract fun exerciseRecordDao(): ExerciseRecordDao

    companion object {
        const val DATABASE_NAME = "undabang.db"
    }
}
