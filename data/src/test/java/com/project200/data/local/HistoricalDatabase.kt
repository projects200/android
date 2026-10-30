package com.project200.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.json.JSONObject

/** 추적된 과거 스키마만 사용하며 현재 스키마의 버전이나 해시는 조작하지 않습니다 */
fun createHistoricalDatabase(
    context: Context,
    name: String,
    version: Int,
    seed: (SupportSQLiteDatabase) -> Unit = {},
) {
    val schema =
        UndabangDatabase::class.java.classLoader!!
            .getResourceAsStream("com.project200.data.local.UndabangDatabase/$version.json")!!
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
    val configuration =
        SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = schema.getJSONArray("entities")
                        for (index in 0 until entities.length()) {
                            val entity = entities.getJSONObject(index)
                            val table = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                            val indices = entity.getJSONArray("indices")
                            for (position in 0 until indices.length()) {
                                db.execSQL(indices.getJSONObject(position).getString("createSql").replace("\${TABLE_NAME}", table))
                            }
                        }
                        val setup = schema.getJSONArray("setupQueries")
                        for (index in 0 until setup.length()) db.execSQL(setup.getString(index))
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = error("UNEXPECTED_FIXTURE_UPGRADE")
                },
            )
            .build()
    FrameworkSQLiteOpenHelperFactory().create(configuration).use { seed(it.writableDatabase) }
}
