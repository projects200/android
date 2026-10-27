package com.project200.undabang.e2e

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.icu.text.DateFormat
import android.media.ExifInterface
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Feed
import com.project200.domain.model.FeedListResult
import com.project200.domain.repository.FeedRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MaestroFeedFixtureTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val repository = mockk<FeedRepository>()
    private val fixture = MaestroFeedFixture(context, MaestroFeedCleaner(repository))
    private val request = MaestroFeatureRequest("feed", "feed-form-validation", "0123456789abcdef", "member", JSONObject())
    private val snapshot = File(context.filesDir, "maestro-feed-fixture.json")

    @Test
    fun `실제 PNG 바이트는 서로 다른 픽셀과 UTC 촬영 메타데이터와 최종 해시를 보존한다`() {
        val owner = MaestroFeedOwnership(request.memberId, request.runId, "feed-photo")
        val images = fixture.images(owner).associateBy { it.label }
        assertThat(images.keys).containsExactly("A", "B")
        val expected =
            listOf(
                Triple("A", Color.RED, "2024:01:02 00:00:00"),
                Triple("B", Color.BLUE, "2024:01:01 00:00:00"),
            )
        for ((label, color, date) in expected) {
            val image = images.getValue(label)
            assertThat(image.name).isEqualTo("maestro-feed-${request.runId}-$label.png")
            assertThat(image.bytes.copyOfRange(0, 8))
                .isEqualTo(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
            val exif = image.bytes.inputStream().use { ExifInterface(it) }
            assertThat(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)).isEqualTo(date)
            assertThat(exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)).isEqualTo("+00:00")
            assertThat(exif.dateTimeOriginal).isEqualTo(image.taken)
            val bitmap = checkNotNull(BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size))
            try {
                assertThat(bitmap.width).isEqualTo(96)
                assertThat(bitmap.height).isEqualTo(96)
                assertThat(bitmap.getPixel(48, 48)).isEqualTo(color)
            } finally {
                bitmap.recycle()
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(image.bytes).joinToString("") { "%02x".format(it) }
            assertThat(image.sha256).isEqualTo(digest)
        }
        assertThat(images.getValue("A").taken).isEqualTo(1704153600000L)
        assertThat(images.getValue("B").taken).isEqualTo(1704067200000L)
        assertThat(images.getValue("A").taken).isGreaterThan(images.getValue("B").taken)
        assertThat(images.getValue("A").sha256).isNotEqualTo(images.getValue("B").sha256)
    }

    @Test
    fun `picker 촬영 설명은 기기의 locale과 timezone을 사용한다`() {
        val originalLocale = Locale.getDefault()
        val originalZone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.KOREA)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
            val korean = fixture.captureText(1704153600000)
            assertThat(DateFormat.getInstanceForSkeleton("MMMdyhmmss", Locale.KOREA).parse(korean).time).isEqualTo(1704153600000L)
            Locale.setDefault(Locale.US)
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val english = fixture.captureText(1704153600000)
            assertThat(DateFormat.getInstanceForSkeleton("MMMdyhmmss", Locale.US).parse(english).time).isEqualTo(1704153600000L)
            assertThat(english).isNotEqualTo(korean)
        } finally {
            Locale.setDefault(originalLocale)
            TimeZone.setDefault(originalZone)
        }
    }

    @Test
    fun `기준 목록 조회 실패는 변경 전 소유 저널을 남기고 정리 재시도는 저장소를 다시 호출하지 않는다`() =
        runTest {
            coEvery { repository.getFeeds(null, 10) } returns BaseResult.Error("SERVER_ERROR", "")

            expectFailure("feed_fixture_read_failed") { fixture.prepare(request) }
            val saved = snapshot.readBytes()
            val journal = JSONObject(saved.toString(Charsets.UTF_8))
            assertThat(journal.getString("memberId")).isEqualTo(request.memberId)
            assertThat(journal.getString("runId")).isEqualTo(request.runId)
            assertThat(journal.getString("flow")).isEqualTo(request.flow)
            assertThat(journal.getString("phase")).isEqualTo("pre-mutation")
            assertThat(journal.has("baseline")).isTrue()
            assertThat(journal.isNull("baseline")).isTrue()

            fixture.cleanup(request)
            assertThat(snapshot.readBytes()).isEqualTo(saved)
            fixture.cleanup(request)
            assertThat(snapshot.readBytes()).isEqualTo(saved)
            coVerify(exactly = 1) { repository.getFeeds(any(), any()) }
            coVerify(exactly = 0) {
                repository.getFeedDetail(any())
                repository.getComments(any())
                repository.deleteFeed(any())
                repository.deleteComment(any())
                repository.deleteFeedImage(any(), any())
            }
        }

    @Test
    fun `변경 전 저널도 다른 실행 작성자 흐름의 준비와 정리를 거부하고 보존한다`() =
        runTest {
            coEvery { repository.getFeeds(null, 10) } returns BaseResult.Error("SERVER_ERROR", "")
            expectFailure("feed_fixture_read_failed") { fixture.prepare(request) }
            val saved = snapshot.readBytes()

            val otherRequests =
                listOf(
                    request.copy(runId = "1123456789abcdef"),
                    request.copy(memberId = "other"),
                    request.copy(flow = "feed-comment"),
                )
            for (other in otherRequests) {
                expectFailure("feed_fixture_snapshot_mismatch") { fixture.prepare(other) }
                expectFailure("feed_fixture_snapshot_mismatch") { fixture.cleanup(other) }
                assertThat(snapshot.readBytes()).isEqualTo(saved)
            }
            coVerify(exactly = 1) { repository.getFeeds(any(), any()) }
            coVerify(exactly = 0) { repository.deleteFeed(any()) }
        }

    @Test
    fun `동일 요청의 재준비는 변경 전 저널을 덮어쓰지 않는다`() =
        runTest {
            coEvery { repository.getFeeds(null, 10) } returns BaseResult.Error("SERVER_ERROR", "")
            expectFailure("feed_fixture_read_failed") { fixture.prepare(request) }
            val saved = snapshot.readBytes()
            posts(existingFeed(42))

            expectFailure("feed_fixture_snapshot_exists") { fixture.prepare(request) }
            assertThat(snapshot.readBytes()).isEqualTo(saved)
            coVerify(exactly = 1) { repository.getFeeds(any(), any()) }
            coVerify(exactly = 0) { repository.deleteFeed(any()) }
        }

    @Test
    fun `동일 요청의 재준비는 완성된 기준 목록 저널을 덮어쓰지 않는다`() =
        runTest {
            posts(existingFeed(42))
            fixture.prepare(request)
            val saved = snapshot.readBytes()
            posts(existingFeed(55))

            expectFailure("feed_fixture_snapshot_exists") { fixture.prepare(request) }
            assertThat(snapshot.readBytes()).isEqualTo(saved)
            coVerify(exactly = 3) { repository.getFeeds(any(), any()) }
            coVerify(exactly = 0) { repository.deleteFeed(any()) }
        }

    @Test
    fun `기준 목록은 변경 전 저널에서 읽고 실제 정리 전에 완성된 저널로 교체한다`() =
        runTest {
            val phases = mutableListOf<String>()
            coEvery { repository.getFeeds(null, 10) } answers {
                val journal = JSONObject(snapshot.readText())
                phases.add(journal.getString("phase"))
                if (journal.getString("phase") == "mutation-authorized") {
                    assertThat(journal.getString("baseline")).isEqualTo("42")
                }
                BaseResult.Success(FeedListResult(false, listOf(existingFeed(42))))
            }

            fixture.prepare(request)

            assertThat(phases).containsExactly("pre-mutation", "mutation-authorized", "mutation-authorized").inOrder()
            assertThat(JSONObject(snapshot.readText()).getString("phase")).isEqualTo("mutation-authorized")
            coVerify(exactly = 0) { repository.deleteFeed(any()) }
        }

    @Test
    fun `후속 공통 정리 실패 뒤 동일 요청 정리는 원본 기준 목록과 저널을 그대로 재사용한다`() =
        runTest {
            posts(existingFeed(42), existingFeed(7))
            fixture.prepare(request)
            val saved = snapshot.readBytes()
            assertThat(JSONObject(saved.toString(Charsets.UTF_8)).getString("baseline")).isEqualTo("7,42")

            fixture.cleanup(request)
            assertThat(snapshot.readBytes()).isEqualTo(saved)
            fixture.cleanup(request)
            assertThat(snapshot.readBytes()).isEqualTo(saved)
            coVerify(exactly = 0) { repository.deleteFeed(any()) }
        }

    @Test
    fun `정리 성공 뒤에도 다른 실행 작성자 흐름은 기존 저널을 다시 소유하지 못한다`() =
        runTest {
            posts(existingFeed(42))
            fixture.prepare(request)
            fixture.cleanup(request)
            val saved = snapshot.readBytes()

            val otherRequests =
                listOf(
                    request.copy(runId = "1123456789abcdef"),
                    request.copy(memberId = "other"),
                    request.copy(flow = "feed-comment"),
                )
            for (other in otherRequests) {
                expectFailure("feed_fixture_snapshot_mismatch") { fixture.cleanup(other) }
                assertThat(snapshot.readBytes()).isEqualTo(saved)
            }
            coVerify(exactly = 0) { repository.deleteFeed(any()) }
        }

    @Test
    fun `재시도에서 기준 목록이 바뀌면 원본 증거를 보존하고 계속 실패한다`() =
        runTest {
            posts(existingFeed(42))
            fixture.prepare(request)
            fixture.cleanup(request)
            val saved = snapshot.readBytes()
            posts(existingFeed(42), existingFeed(55))

            expectFailure("feed_validation_unexpected_posts") { fixture.cleanup(request) }
            assertThat(snapshot.readBytes()).isEqualTo(saved)
            coVerify(exactly = 0) { repository.deleteFeed(any()) }
        }

    @Test
    fun `완성된 저널의 기준 목록이 유실되면 변경 전 준비 실패로 간주하지 않는다`() =
        runTest {
            posts(existingFeed(42))
            fixture.prepare(request)
            val journal = JSONObject(snapshot.readText())
            for (missing in listOf(false, true)) {
                if (missing) journal.remove("baseline") else journal.put("baseline", JSONObject.NULL)
                snapshot.writeText(journal.toString())
                val saved = snapshot.readBytes()

                expectFailure("feed_validation_unexpected_posts") { fixture.cleanup(request) }
                assertThat(snapshot.readBytes()).isEqualTo(saved)
            }
            coVerify(exactly = 3) { repository.getFeeds(any(), any()) }
            coVerify(exactly = 0) { repository.deleteFeed(any()) }
        }

    @Test
    fun `깨진 JSON이나 없거나 알 수 없는 단계는 준비와 정리를 허용하지 않는다`() =
        runTest {
            posts(existingFeed(42))
            fixture.prepare(request)
            val complete = snapshot.readText()
            val invalidJournals =
                listOf(
                    "{",
                    JSONObject(complete).apply { remove("phase") }.toString(),
                    JSONObject(complete).put("phase", "unknown").toString(),
                )
            for (invalid in invalidJournals) {
                snapshot.writeText(invalid)
                val saved = snapshot.readBytes()

                expectFailure("feed_fixture_snapshot_invalid") { fixture.prepare(request) }
                expectFailure("feed_fixture_snapshot_invalid") { fixture.cleanup(request) }
                assertThat(snapshot.readBytes()).isEqualTo(saved)
            }
            coVerify(exactly = 3) { repository.getFeeds(any(), any()) }
            coVerify(exactly = 0) { repository.deleteFeed(any()) }
        }

    @Test
    fun `변경 전 단계라도 불완전하거나 완성된 증거가 섞인 저널은 정리 성공으로 간주하지 않는다`() =
        runTest {
            coEvery { repository.getFeeds(null, 10) } returns BaseResult.Error("SERVER_ERROR", "")
            expectFailure("feed_fixture_read_failed") { fixture.prepare(request) }
            val original = snapshot.readText()
            val invalidJournals =
                listOf(
                    JSONObject(original).apply { remove("baseline") },
                    JSONObject(original).put("baseline", "42"),
                    JSONObject(original).put("mediaPath", "Pictures/other/"),
                    JSONObject(original).put("A", JSONObject()),
                    JSONObject(original).put("B", JSONObject()),
                )
            for (invalid in invalidJournals) {
                snapshot.writeText(invalid.toString())
                val saved = snapshot.readBytes()

                expectFailure("feed_fixture_snapshot_invalid") { fixture.cleanup(request) }
                assertThat(snapshot.readBytes()).isEqualTo(saved)
            }
            coVerify(exactly = 1) { repository.getFeeds(any(), any()) }
            coVerify(exactly = 0) { repository.deleteFeed(any()) }
        }

    @Test
    fun `변경 흐름도 저널 없이 정리를 성공으로 간주하지 않는다`() =
        runTest {
            for (flow in listOf("feed-post-crud", "feed-like", "feed-comment", "feed-photo")) {
                expectFailure("feed_fixture_snapshot_invalid") { fixture.cleanup(request.copy(flow = flow)) }
            }
            coVerify(exactly = 0) { repository.getFeeds(any(), any()) }
            coVerify(exactly = 0) { repository.deleteFeed(any()) }
        }

    @Test
    fun `기준 목록이 없으면 유효성 검사 정리를 성공으로 간주하지 않는다`() =
        runTest {
            expectFailure("feed_validation_unexpected_posts") { fixture.cleanup(request) }
            coVerify(exactly = 0) { repository.getFeeds(any(), any()) }
        }

    private fun posts(vararg feeds: Feed) {
        coEvery { repository.getFeeds(null, 10) } returns BaseResult.Success(FeedListResult(false, feeds.toList()))
    }

    private fun existingFeed(id: Long) =
        Feed(
            id,
            "ordinary post",
            0,
            0,
            null,
            null,
            null,
            LocalDateTime.of(2026, 10, 15, 12, 0),
            false,
            false,
            request.memberId,
            "CI",
            null,
            null,
            emptyList(),
        )

    private suspend fun expectFailure(
        reason: String,
        action: suspend () -> Unit,
    ) {
        try {
            action()
            throw AssertionError("정리 실패가 허용되었습니다")
        } catch (failure: MaestroSetupFailure) {
            assertThat(failure.reason).isEqualTo(reason)
        }
    }
}
