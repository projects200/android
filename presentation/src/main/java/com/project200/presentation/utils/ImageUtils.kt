package com.project200.presentation.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ImageUtils {
    private const val TAG = "ImageUtils"
    /**
     * 이미지를 압축하여 지정된 최대 너비/높이 및 품질로 새 파일 URI를 반환합니다.
     * JPEG/JPG는 JPEG으로, PNG는 PNG로 압축됩니다. 그 외는 JPEG으로 압축됩니다.
     *
     * imageUri 원본 이미지의 URI
     * targetMaxWidth 압축 후 최대 너비 (픽셀)
     * targetMaxHeight 압축 후 최대 높이 (픽셀)
     * jpegQuality JPEG으로 압축할 경우의 품질 (0-100, 기본값 80)
     *
     * return 압축된 이미지 파일의 URI, 실패 시 null
     */
    fun compressImage(
        context: Context,
        imageUri: Uri,
        targetMaxWidth: Int = 1080,
        targetMaxHeight: Int = 1920,
        jpegQuality: Int = 80
    ): Uri? {
        var inputStream: InputStream? = null
        var rotatedBitmap: Bitmap? = null // 최종적으로 압축될 비트맵
        var originalBitmap: Bitmap? = null // 최초 로드된 비트맵

        try {
            // 이미지 경계만 읽어와서 샘플링 크기 계산 준비
            inputStream = context.contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                Timber.tag(TAG).d("inputStream 읽기 실패: $imageUri")
                return null
            }

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close() // 스트림 닫기

            // 이미지 유효성 확인
            if (options.outWidth == -1 || options.outHeight == -1) {
                Timber.tag(TAG).d("유효하지 않은 이미지: $imageUri")
                return null
            }

            // 샘플링 사이즈 계산
            options.inSampleSize = calculateInSampleSize(options, targetMaxWidth, targetMaxHeight)
            options.inJustDecodeBounds = false // 실제 이미지 로드하도록 설정

            // 샘플링된 Bitmap 로드
            inputStream = context.contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                Timber.tag(TAG).d("inputStream reopen 실패: $imageUri")
                return null
            }
            originalBitmap = BitmapFactory.decodeStream(inputStream, null, options)
            if (originalBitmap == null) {
                Timber.tag(TAG).d("bitmap 디코딩 실패")
                return null
            }

            // 이미지 회전 정보 보정
            rotatedBitmap = rotateImageIfRequired(context, originalBitmap, imageUri)

            // 출력 포맷 및 확장자 결정
            val mimeType = context.contentResolver.getType(imageUri)
            val outputFormat: Bitmap.CompressFormat
            val outputExtension: String

            if (mimeType != null && mimeType.startsWith("image/png", ignoreCase = true)) {
                outputFormat = Bitmap.CompressFormat.PNG
                outputExtension = ".png"
                Timber.tag(TAG).d("Compressing as PNG")
            } else { // 기본적으로 JPEG 사용 (jpg, jpeg, 기타 이미지 타입 포함)
                outputFormat = Bitmap.CompressFormat.JPEG
                outputExtension = ".jpg"
                Timber.tag(TAG).d("Compressing as JPEG")
            }

            // Bitmap 압축
            val byteArrayOutputStream = ByteArrayOutputStream()
            val qualityToUse = if (outputFormat == Bitmap.CompressFormat.JPEG) jpegQuality else 100
            rotatedBitmap.compress(outputFormat, qualityToUse, byteArrayOutputStream)

            // 압축된 Bitmap을 파일로 저장
            // 파일 이름에 compressed 추가
            val tempFileName = "compressed_${System.currentTimeMillis()}$outputExtension"
            val tempFile = File(context.cacheDir, tempFileName)
            FileOutputStream(tempFile).use { fos ->
                fos.write(byteArrayOutputStream.toByteArray())
            }
            Timber.tag(TAG).d("이미지 압축 성공: ${tempFile.absolutePath}, Size: ${tempFile.length() / 1024}KB")

            return Uri.fromFile(tempFile)

        } catch (e: Exception) {
            Timber.tag(TAG).d(e, "이미지 압축 실패")
            return null // 오류 발생 시 null 반환
        } finally {
            inputStream?.close()
        }
    }

    /**
     * BitmapFactory.Options와 목표 너비/높이를 기반으로 적절한 inSampleSize 값을 계산합니다.
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            // 가로, 세로 모두 목표 크기보다 클 동안 샘플링 크기를 2배씩 늘립니다.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * EXIF 정보를 읽어 이미지를 올바른 방향으로 회전시킵니다.
     * return 회전된 Bitmap
     */
    private fun rotateImageIfRequired(context: Context, originalBitmap: Bitmap, imageUri: Uri): Bitmap {
        var inputStreamForExif: InputStream? = null
        try {
            inputStreamForExif = context.contentResolver.openInputStream(imageUri)
            if (inputStreamForExif == null) {
                Timber.tag(TAG).d("EXIF의 inputStream open 실패")
                return originalBitmap
            }

            val exif = ExifInterface(inputStreamForExif)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

            val matrix = Matrix()
            var needsRotation = true
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1.0f, 1.0f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1.0f, -1.0f)
                else -> needsRotation = false // 회전 불필요
            }

            if (!needsRotation) {
                return originalBitmap
            }

            val rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)

            // 원본 비트맵과 새로 생성된 비트맵이 다를 경우에만 원본을 recycle 합니다.
            if (rotatedBitmap != originalBitmap) {
                originalBitmap.recycle()
            }
            return rotatedBitmap
        } catch (e: Exception) {
            Timber.tag(TAG).d(e, "이미지 회전 중 실패")
            return originalBitmap // 오류 발생 시 원본 Bitmap 반환
        } finally {
            inputStreamForExif?.close()
        }
    }
}