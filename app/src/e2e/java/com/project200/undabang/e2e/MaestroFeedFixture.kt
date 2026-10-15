package com.project200.undabang.e2e

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

internal class MaestroFeedFixture
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val cleaner: MaestroFeedCleaner,
    ) : MaestroFeatureFixture {
        private val snapshot = AtomicFile(File(context.filesDir, "maestro-feed-fixture.json"))
        private val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        override suspend fun prepare(request: MaestroFeatureRequest): Map<String, String> {
            val owner = ownership(request)
            if (checkSnapshot(owner) != null) throw MaestroSetupFailure("feed_fixture_snapshot_exists")
            save(owner, "pre-mutation", emptyList(), null)
            // 촬영 메타데이터를 포함한 최종 PNG 바이트와 소유 경로를 삽입·UI 변경 전에 확정합니다.
            val images = if (owner.flow == "feed-photo") images(owner) else emptyList()
            val baseline = if (owner.flow == "feed-form-validation") baseline(owner) else null
            save(owner, "mutation-authorized", images, baseline)
            cleaner.clean(owner)
            for (image in images) importImage(image, owner)
            return buildMap {
                put("FEED_POST", owner.post)
                put("FEED_EDITED_POST", owner.editedPost)
                put("FEED_COMMENT", owner.comment)
                put("FEED_REPLY", owner.reply)
                for (image in images) {
                    put("FEED_IMAGE_${image.label}_NAME", image.name)
                    put("FEED_IMAGE_${image.label}_SHA256", image.sha256)
                    put("FEED_IMAGE_${image.label}_TAKEN", image.taken.toString())
                }
            }
        }

        override suspend fun cleanup(request: MaestroFeatureRequest) {
            val owner = ownership(request)
            val saved =
                checkSnapshot(owner) ?: throw MaestroSetupFailure(
                    if (owner.flow == "feed-form-validation") "feed_validation_unexpected_posts" else "feed_fixture_snapshot_invalid",
                )
            // 변경을 허용하기 전 실패한 준비만 부작용 없이 정리하며 공통 정리 재시도 증거를 보존합니다.
            if (saved.getString("phase") == "pre-mutation") return
            if (owner.flow == "feed-form-validation") {
                if (saved.isNull("baseline") || saved.getString("baseline") != baseline(owner)) {
                    // 빈 본문 오류로 생긴 무표식 글을 임의로 소유한다고 판단해 삭제하지 않습니다.
                    throw MaestroSetupFailure("feed_validation_unexpected_posts")
                }
            }
            cleaner.clean(owner)
            if (owner.flow == "feed-photo") removeMedia(owner)
            // 후속 공통 정리·세션 제거 실패 시 같은 ownership으로 재시도할 기준 증거를 남깁니다.
            // 앱 private 저널은 공통 정리 뒤 호스트의 최종 pm clear 성공으로만 제거합니다.
        }

        private fun ownership(request: MaestroFeatureRequest): MaestroFeedOwnership {
            if (request.feature != "feed") throw MaestroSetupFailure("feed_fixture_identity_invalid")
            return MaestroFeedOwnership(request.memberId, request.runId, request.flow)
        }

        private fun checkSnapshot(owner: MaestroFeedOwnership): JSONObject? {
            if (!snapshot.baseFile.exists() && !File(snapshot.baseFile.path + ".bak").exists()) {
                if (File(snapshot.baseFile.path + ".new").exists()) throw MaestroSetupFailure("feed_fixture_snapshot_invalid")
                return null
            }
            val saved =
                try {
                    JSONObject(snapshot.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
                } catch (_: Exception) {
                    throw MaestroSetupFailure("feed_fixture_snapshot_invalid")
                }
            if (
                saved.optString("memberId") != owner.memberId || saved.optString("runId") != owner.runId ||
                saved.optString("flow") != owner.flow
            ) {
                throw MaestroSetupFailure("feed_fixture_snapshot_mismatch")
            }
            val phase = saved.optString("phase")
            if (phase != "pre-mutation" && phase != "mutation-authorized") {
                throw MaestroSetupFailure("feed_fixture_snapshot_invalid")
            }
            if (phase == "pre-mutation") {
                if (
                    saved.optString("mediaPath") != mediaPath(owner) || !saved.has("baseline") || !saved.isNull("baseline") ||
                    saved.has("A") || saved.has("B")
                ) {
                    throw MaestroSetupFailure("feed_fixture_snapshot_invalid")
                }
            }
            return saved
        }

        private fun save(
            owner: MaestroFeedOwnership,
            phase: String,
            images: List<Image>,
            baseline: String?,
        ) {
            val json =
                JSONObject()
                    .put("memberId", owner.memberId)
                    .put("runId", owner.runId)
                    .put("flow", owner.flow)
                    .put("phase", phase)
                    .put("mediaPath", mediaPath(owner))
                    .put("baseline", baseline ?: JSONObject.NULL)
            for (image in images) {
                json.put(image.label, JSONObject().put("name", image.name).put("sha256", image.sha256).put("taken", image.taken))
            }
            val stream = snapshot.startWrite()
            try {
                stream.write(json.toString().toByteArray(Charsets.UTF_8))
                snapshot.finishWrite(stream)
            } catch (_: Exception) {
                snapshot.failWrite(stream)
                throw MaestroSetupFailure("feed_fixture_snapshot_failed")
            }
        }

        private suspend fun baseline(owner: MaestroFeedOwnership): String = cleaner.memberPostIds(owner).sorted().joinToString(",")

        internal data class Image(
            val label: String,
            val name: String,
            val bytes: ByteArray,
            val taken: Long,
            val sha256: String,
        )

        internal fun images(owner: MaestroFeedOwnership): List<Image> =
            listOf(image(owner, "B", Color.BLUE, 1704067200000), image(owner, "A", Color.RED, 1704153600000))

        private fun image(
            owner: MaestroFeedOwnership,
            label: String,
            color: Int,
            taken: Long,
        ): Image {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) throw MaestroSetupFailure("feed_fixture_media_failed")
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            val bytes =
                try {
                    val file = File.createTempFile("maestro-feed-", ".png", context.cacheDir)
                    try {
                        bitmap.eraseColor(color)
                        file.outputStream().use { output ->
                            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                                throw MaestroSetupFailure("feed_fixture_media_failed")
                            }
                        }
                        val date = CAPTURE_DATE_FORMAT.format(Instant.ofEpochMilli(taken))
                        // API 33 framework ExifInterface가 PNG eXIf 청크에 저장합니다.
                        // offset이 있어야 스캐너가 파일 수정 시각으로 촬영 시간대를 추정하지 않습니다.
                        ExifInterface(file.path).apply {
                            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, date)
                            setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, "+00:00")
                            saveAttributes()
                        }
                        if (ExifInterface(file.path).dateTimeOriginal != taken) {
                            throw MaestroSetupFailure("feed_fixture_media_failed")
                        }
                        file.readBytes()
                    } finally {
                        file.delete()
                    }
                } finally {
                    bitmap.recycle()
                }
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            return Image(label, "maestro-feed-${owner.runId}-$label.png", bytes, taken, digest)
        }

        private fun importImage(
            image: Image,
            owner: MaestroFeedOwnership,
        ) {
            val values =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, image.name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, mediaPath(owner))
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            val uri = context.contentResolver.insert(collection, values) ?: throw MaestroSetupFailure("feed_fixture_media_failed")
            val output = context.contentResolver.openOutputStream(uri) ?: throw MaestroSetupFailure("feed_fixture_media_failed")
            output.use { it.write(image.bytes) }
            val published =
                context.contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
            if (published != 1) throw MaestroSetupFailure("feed_fixture_media_failed")
        }

        private fun removeMedia(owner: MaestroFeedOwnership) {
            val resolver = context.contentResolver
            val names = arrayOf("maestro-feed-${owner.runId}-A.png", "maestro-feed-${owner.runId}-B.png")
            // 재시도 시 부분 삽입도 회수하지만 일반 갤러리 파일은 조회·삭제하지 않습니다.
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND ${MediaStore.Images.Media.DISPLAY_NAME} IN (?, ?)"
            val arguments = arrayOf(mediaPath(owner), *names)
            val ids = mutableListOf<Long>()
            val cursor =
                resolver.query(collection, arrayOf(MediaStore.Images.Media._ID), selection, arguments, null)
                    ?: throw MaestroSetupFailure("feed_fixture_media_cleanup_failed")
            cursor.use {
                while (it.moveToNext()) ids.add(it.getLong(0))
            }
            for (id in ids) {
                val uri = Uri.withAppendedPath(collection, id.toString())
                if (resolver.delete(uri, null, null) != 1) throw MaestroSetupFailure("feed_fixture_media_cleanup_failed")
            }
            val remaining =
                resolver.query(collection, arrayOf(MediaStore.Images.Media._ID), selection, arguments, null)
                    ?: throw MaestroSetupFailure("feed_fixture_media_cleanup_failed")
            remaining.use {
                if (it.moveToFirst()) throw MaestroSetupFailure("feed_fixture_media_cleanup_failed")
            }
        }

        private fun mediaPath(owner: MaestroFeedOwnership) = "Pictures/maestro-feed/${owner.runId}/"

        private companion object {
            val CAPTURE_DATE_FORMAT: DateTimeFormatter =
                DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT).withZone(ZoneOffset.UTC)
        }
    }
