package com.project200.data.local

import androidx.room.Room
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.robolectric.RuntimeEnvironment

/**
 * 메모리 DB를 엽니다.
 *
 * 실제 스키마와 TypeConverter를 그대로 써서 DAO의 SQL을 검증합니다.
 * `allowMainThreadQueries`는 테스트에서 `runTest`로 바로 호출하기 위한 것입니다
 */
fun createInMemoryDatabase(): UndabangDatabase {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    return Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        UndabangDatabase::class.java,
    )
        .addTypeConverter(UndabangTypeConverters(moshi))
        .allowMainThreadQueries()
        .build()
}
