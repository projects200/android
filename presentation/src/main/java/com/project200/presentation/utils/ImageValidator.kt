package com.project200.presentation.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.project200.common.constants.RuleConstants.ALLOWED_EXTENSIONS
import com.project200.common.constants.RuleConstants.MAX_FILE_SIZE_BYTES

object ImageValidator {
    // 파일 유효성 검사 함수
    fun validateImageFile(uri: Uri, context: Context): Pair<Boolean, String?> {
        // 파일 크기 검사
        context.contentResolver.openFileDescriptor(uri, "r")?.use { parcelFileDescriptor ->
            if (parcelFileDescriptor.statSize > MAX_FILE_SIZE_BYTES) {
                return Pair(false, OVERSIZE)
            }
        } ?: return Pair(false, FAIL_TO_READ) // 파일 디스크립터를 열 수 없는 경우

        // 파일 확장자/MIME 타입 검사
        val fileExtension = getFileExtension(uri, context)
        if (fileExtension == null || !ALLOWED_EXTENSIONS.contains(fileExtension.lowercase())) {
            // MIME 타입으로 한 번 더 확인 (확장자가 없는 경우 등 대비)
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType == null || !ALLOWED_EXTENSIONS.contains(MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)?.lowercase())) {
                return Pair(false, INVALID_TYPE)
            }
        }
        return Pair(true, null)
    }

    // URI로부터 파일 확장자 가져오기
    private fun getFileExtension(uri: Uri, context: Context): String? {
        var extension: String? = null
        val contentResolver = context.contentResolver
        // ContentResolver를 통해 파일 이름에서 확장자 추출
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                if (displayName.lastIndexOf('.') != -1) {
                    extension = displayName.substring(displayName.lastIndexOf('.') + 1)
                }
            }
        }
        // MIME 타입에서 확장자 추론 (위 방법 실패 시 대체)
        if (extension == null) {
            val mimeType = contentResolver.getType(uri)
            if (mimeType != null) {
                extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            }
        }
        return extension?.lowercase()
    }

    const val OVERSIZE = "oversize"
    const val INVALID_TYPE = "invalid_type"
    const val FAIL_TO_READ = "fail_to_read"
}