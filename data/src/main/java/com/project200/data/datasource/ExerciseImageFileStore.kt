package com.project200.data.datasource

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 파일 준비부터 DB 확정·정리까지 명시적 세션 삭제와 같은 락을 사용합니다. */
@Singleton
class ExerciseImageFileStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val mutex = Mutex()
        private val directory = File(context.filesDir, "exercise-images")

        @Volatile
        var sessionRevision: Long = 0
            private set

        suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

        fun invalidateSession() {
            sessionRevision++
        }

        /** 폼에서 검증·압축한 바이트를 재인코딩하지 않고 한 번 복사합니다. */
        suspend fun prepare(uris: List<String>): List<PreparedExerciseImage> {
            if (uris.isEmpty()) return emptyList()
            val prepared = mutableListOf<PreparedExerciseImage>()
            try {
                check(directory.isDirectory || directory.mkdirs()) { "IMAGE_DIRECTORY_FAILED" }
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                val resolver = context.contentResolver
                for (source in uris) {
                    currentCoroutineContext().ensureActive()
                    val uri = Uri.parse(source)
                    require(uri.scheme == "content" || uri.scheme == "file") { "INVALID_IMAGE_URI" }
                    val suffix =
                        resolver.getType(uri)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                            ?: sourceExtension(uri)
                    val mime = suffix?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
                    require(mime?.startsWith("image/") == true && suffix != null) { "UNKNOWN_IMAGE_TYPE" }
                    val id = UUID.randomUUID().toString()
                    val file = File(directory, "$id.$suffix")
                    check(file.createNewFile()) { "IMAGE_FILE_EXISTS" }
                    val image = PreparedExerciseImage(id, UUID.randomUUID().toString(), file.absolutePath)
                    prepared.add(image)
                    resolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(file).use { output ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                            output.fd.sync()
                        }
                    } ?: throw IOException("IMAGE_SOURCE_UNREADABLE")
                    check(file.length() > 0L) { "EMPTY_IMAGE" }
                }
                currentCoroutineContext().ensureActive()
                return prepared
            } catch (failure: Throwable) {
                prepared.forEach { image ->
                    try {
                        deleteOwned(image.path)
                    } catch (cleanup: Exception) {
                        failure.addSuppressed(cleanup)
                    }
                }
                throw failure
            }
        }

        private fun sourceExtension(uri: Uri): String? {
            val name =
                if (uri.scheme == "file") {
                    uri.lastPathSegment
                } else {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
                    }
                }
            return name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotBlank() }
        }

        /** DB가 참조하지 않는다고 확인한 파일에만 호출합니다. 원본 URI와 외부 경로는 삭제하지 않습니다. */
        fun deleteOwned(path: String) {
            val file = File(path)
            if (
                file.parentFile?.canonicalFile != directory.canonicalFile ||
                file.canonicalFile.parentFile != directory.canonicalFile
            ) {
                return
            }
            if (file.exists() && !file.delete()) throw IOException("IMAGE_DELETE_FAILED")
        }

        fun clearOwnedFiles() {
            directory.listFiles()?.forEach { deleteOwned(it.absolutePath) }
        }
    }

data class PreparedExerciseImage(
    val localId: String,
    val requestId: String,
    val path: String,
)
