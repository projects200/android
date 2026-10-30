package com.project200.data.datasource

import com.google.common.truth.Truth.assertThat
import com.project200.data.api.ApiService
import com.project200.data.dto.PatchExerciseRequestDto
import com.project200.data.dto.PostExerciseRequestDto
import com.project200.data.utils.ExerciseSyncAccount
import com.project200.data.utils.LocalDateAdapter
import com.project200.data.utils.LocalDateTimeAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.UUID

/** 실제 Retrofit 어노테이션과 Moshi 직렬화를 사용합니다. HTTP 경계만 메모리 응답입니다. */
@RunWith(RobolectricTestRunner::class)
class ExerciseSyncTransportTest {
    private val account = ExerciseSyncAccount("member", 1, 2)
    private val key = UUID.randomUUID().toString()
    private val imageId = UUID.randomUUID().toString()
    private lateinit var request: Request
    private lateinit var remote: ExerciseRecordRemoteDataSource
    private var data = "null"

    @Before
    fun setUp() {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).add(LocalDateAdapter()).add(LocalDateTimeAdapter()).build()
        val api = Retrofit.Builder().baseUrl("https://example.test/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .callFactory(object : Call.Factory {
                override fun newCall(incoming: Request): Call {
                    request = incoming
                    val call = mockk<Call>(relaxed = true)
                    every { call.request() } returns incoming
                    every { call.enqueue(any()) } answers {
                        firstArg<Callback>().onResponse(
                            call,
                            Response.Builder().request(incoming).protocol(Protocol.HTTP_1_1)
                                .code(200).message("OK")
                                .body("{\"succeed\":true,\"code\":\"OK\",\"message\":\"\",\"data\":$data}".toResponseBody("application/json".toMediaType()))
                                .build(),
                        )
                    }
                    return call
                }
            }).build().create(ApiService::class.java)
        remote = ExerciseRecordRemoteDataSource(api)
    }

    private fun body(): JSONObject {
        val buffer = Buffer()
        requireNotNull(request.body).writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    @Test
    fun `생성은 불변 생성키를 본문에 넣고 실제 version 응답을 읽는다`() = runTest {
        data = "{\"exerciseId\":42,\"earnedPoints\":3,\"version\":0}"
        val response = remote.postExerciseRecord(PostExerciseRequestDto("A", "OTHER", "장소", "본문", "2026-10-20T09:00:00", "2026-10-20T10:00:00", key), account)
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/exercises")
        assertThat(body().getString("createRequestId")).isEqualTo(key)
        assertThat(request.tag(ExerciseSyncAccount::class.java)).isEqualTo(account)
        assertThat(response.data?.exerciseId).isEqualTo(42)
        assertThat(response.data?.version).isEqualTo(0)
    }

    @Test
    fun `수정은 editId와 baseVersion을 같은 실제 PATCH 본문에 보낸다`() = runTest {
        data = "{\"exerciseId\":42,\"version\":8}"
        val response = remote.patchExerciseRecord(42, PatchExerciseRequestDto("B", "OTHER", "장소", "본문", "2026-10-20T09:00:00", "2026-10-20T10:00:00", key, 7), account)
        assertThat(request.method).isEqualTo("PATCH")
        assertThat(body().getString("editId")).isEqualTo(key)
        assertThat(body().getLong("baseVersion")).isEqualTo(7)
        assertThat(response.data?.version).isEqualTo(8)
    }

    @Test
    fun `사진은 pictures multipart와 query 키 및 순서가 일치하는 매핑을 읽는다`() = runTest {
        data = "{\"exerciseId\":42,\"version\":9,\"pictures\":[{\"clientImageId\":\"$imageId\",\"pictureId\":91,\"pictureUrl\":\"https://example.test/p.png\"}]}"
        val part = MultipartBody.Part.createFormData("pictures", "original.png", byteArrayOf(1, 2, 3).toRequestBody("image/png".toMediaType()))
        val response = remote.postExerciseImages(42, listOf(part), key, listOf(imageId), account)
        assertThat(request.url.queryParameter("uploadRequestId")).isEqualTo(key)
        assertThat(request.url.queryParameterValues("clientImageIds")).containsExactly(imageId).inOrder()
        val multipart = request.body as MultipartBody
        assertThat(multipart.parts.single().headers?.get("Content-Disposition")).contains("name=\"pictures\"")
        assertThat(multipart.parts.single().body.contentType().toString()).isEqualTo("image/png")
        assertThat(response.data?.pictures?.single()?.clientImageId).isEqualTo(imageId)
        assertThat(response.data?.pictures?.single()?.pictureId).isEqualTo(91)
        assertThat(response.data?.version).isEqualTo(9)
    }

    @Test
    fun `삭제 query editId와 실제 null legacy 응답 계약을 함께 보존한다`() = runTest {
        data = "{\"exerciseId\":42,\"version\":10}"
        assertThat(remote.deleteExerciseImages(42, listOf(91), key, account).data?.version).isEqualTo(10)
        assertThat(request.url.queryParameter("editId")).isEqualTo(key)
        assertThat(request.url.queryParameter("pictureIds")).isEqualTo("91")
        assertThat(request.tag(ExerciseSyncAccount::class.java)).isEqualTo(account)
        data = "null"
        assertThat(remote.deleteExerciseRecord(42).data).isNull()
        assertThat(request.url.queryParameter("editId")).isNull()
        assertThat(request.tag(ExerciseSyncAccount::class.java)).isNull()
    }
}
