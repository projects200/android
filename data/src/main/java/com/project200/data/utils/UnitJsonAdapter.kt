package com.project200.data.utils

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

/**
 * JSON의 `null`이 아닌 모든 값(객체, 배열, 문자열 등)을 Kotlin의 `Unit`으로 처리하기 위한 어댑터.
 * 불필요한 데이터를 파싱하지 않고 건너뜁니다.
 */
class UnitJsonAdapter : JsonAdapter<Unit>() {
    override fun fromJson(reader: JsonReader): Unit? {
        // 토큰이 null이면 그대로 null을 반환 (Unit?을 위함)
        if (reader.peek() == JsonReader.Token.NULL) {
            return reader.nextNull()
        }
        // null이 아니라면, 해당 값 전체를 파싱하지 않고 건너뜁니다.
        reader.skipValue()

        return Unit
    }

    override fun toJson(
        writer: JsonWriter,
        value: Unit?,
    ) {
        writer.nullValue()
    }
}
