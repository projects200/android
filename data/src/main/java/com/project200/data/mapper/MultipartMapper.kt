package com.project200.data.mapper

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import timber.log.Timber
import java.io.IOException

fun Uri.toMultipartBodyPart(
    contentResolver: ContentResolver,
    partName: String
): MultipartBody.Part? {
    return try {
        val fileName = contentResolver.getFileName(this) ?: "image_${System.currentTimeMillis()}"
        val mimeType = contentResolver.getType(this) ?: "image/*"

        val requestBody = object : RequestBody() {
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

private fun ContentResolver.getFileName(uri: Uri): String? {
    var name: String? = null
    val cursor = query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                name = it.getString(nameIndex)
            }
        }
    }
    return name
}