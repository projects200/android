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
