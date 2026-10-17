package com.project200.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.project200.data.impl.RoomSessionDataCleaner
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.UndabangTypeConverters
import com.project200.data.local.dao.ExerciseCountDao
import com.project200.data.local.dao.ExerciseRecordDao
import com.project200.data.local.dao.TimerDao
import com.project200.domain.manager.SessionDataCleaner
import com.squareup.moshi.Moshi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {
    @Binds
    @Singleton
    abstract fun bindSessionDataCleaner(roomSessionDataCleaner: RoomSessionDataCleaner): SessionDataCleaner

    companion object {
        /** 메타데이터만 추가합니다. 기존 계정·대기 행·타이머는 재생성하지 않습니다. */
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS exercise_cache_query " +
                            "(memberId TEXT NOT NULL, queryType TEXT NOT NULL, date TEXT NOT NULL, PRIMARY KEY(memberId, queryType, date))",
                    )
                }
            }

        @Provides
        @Singleton
        fun provideUndabangTypeConverters(moshi: Moshi): UndabangTypeConverters {
            return UndabangTypeConverters(moshi)
        }

        /**
         * 2→3은 대기 행을 보존하는 추가 마이그레이션을 씁니다. 미지원 경로의 출시 전 재생성 정책은 유지합니다.
         *
         * 스키마를 바꿀 때는 version을 올립니다. version을 그대로 두고 스키마만 바꾸면
         * identityHash가 어긋나 재생성 없이 크래시합니다
         *
         * 주의: 재생성은 전송 대기 행까지 지웁니다. 출시 뒤에는 이 설정을 걷고
         * 마이그레이션을 씁니다
         */
        @Provides
        @Singleton
        fun provideUndabangDatabase(
            @ApplicationContext context: Context,
            typeConverters: UndabangTypeConverters,
        ): UndabangDatabase {
            return Room.databaseBuilder(
                context,
                UndabangDatabase::class.java,
                UndabangDatabase.DATABASE_NAME,
            )
                .addTypeConverter(typeConverters)
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
        }

        @Provides
        @Singleton
        fun provideExerciseCountDao(database: UndabangDatabase): ExerciseCountDao {
            return database.exerciseCountDao()
        }

        @Provides
        @Singleton
        fun provideExerciseRecordDao(database: UndabangDatabase): ExerciseRecordDao {
            return database.exerciseRecordDao()
        }

        @Provides
        @Singleton
        fun provideTimerDao(database: UndabangDatabase): TimerDao {
            return database.timerDao()
        }
    }
}
