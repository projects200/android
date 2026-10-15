package com.project200.undabang.e2e

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
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
