package com.project200.data.mapper

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import timber.log.Timber
import java.io.File
import java.io.IOException

fun Uri.toMultipartBodyPart(
    context: Context,
    partName: String,
): MultipartBody.Part? {
    val contentResolver = context.contentResolver
    return try {
        val fileName = contentResolver.getFileNameWithExtension(this)
        val mimeType = contentResolver.getType(this) ?: "image/*"

        val requestBody =
            object : RequestBody() {
                override fun contentType() = mimeType.toMediaTypeOrNull()

                override fun writeTo(sink: BufferedSink) {
                    contentResolver.openInputStream(this@toMultipartBodyPart)?.use { inputStream ->
                        sink.writeAll(inputStream.source())
                    } ?: throw IOException("Could not open input stream for URI: $this")
                }
            }

        MultipartBody.Part.createFormData(partName, fileName, requestBody)
    } catch (e: Exception) {
        Timber.e(e, "Multipart 변환 실패: $this")
        null
    }
}

/**
 * URI의 scheme에 따라 적절한 방법으로 파일 이름을 가져옵니다.
 * content:// URI의 경우 ContentResolver를, file:// URI의 경우 파일 경로를 직접 사용합니다.
 * 파일 이름을 찾지 못할 경우 MIME 타입을 기반으로 확장자를 포함한 대체 파일 이름을 생성합니다.
 */
private fun ContentResolver.getFileNameWithExtension(uri: Uri): String {
    val defaultFileName = "image_${System.currentTimeMillis()}"

    when (uri.scheme) {
        ContentResolver.SCHEME_FILE -> {
            // file:// URI의 경우, 경로에서 직접 파일 이름을 가져옵니다. (e.g., "compressed_123.jpg")
            return File(uri.path!!).name
        }
        ContentResolver.SCHEME_CONTENT -> {
            // content:// URI의 경우, 기존 로직을 사용합니다.
            val cursor = query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = it.getString(nameIndex)
                        if (name.isNotEmpty()) return name
                    }
                }
            }
        }
    }

    // 위 방법으로 이름을 찾지 못한 경우 (특히 content:// 에서) MIME 타입으로 확장자를 추론합니다.
    val mimeType = getType(uri)
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
    return if (extension != null) "$defaultFileName.$extension" else "$defaultFileName.jpg"
}