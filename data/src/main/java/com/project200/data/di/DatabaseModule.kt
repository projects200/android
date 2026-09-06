package com.project200.data.di

import android.content.Context
import androidx.room.Room
import com.project200.data.impl.RoomSessionDataCleaner
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.UndabangTypeConverters
import com.project200.data.local.dao.ExerciseCountDao
import com.project200.data.local.dao.ExerciseRecordDao
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
        @Provides
        @Singleton
        fun provideUndabangTypeConverters(moshi: Moshi): UndabangTypeConverters {
            return UndabangTypeConverters(moshi)
        }

        /**
         * 출시 전이라 마이그레이션 대신 재생성을 씁니다.
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
    }
}
