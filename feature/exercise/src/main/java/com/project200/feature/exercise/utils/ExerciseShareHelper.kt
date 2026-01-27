package com.project200.feature.exercise.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.bumptech.glide.Glide
import com.project200.domain.model.ExerciseRecord
import com.project200.feature.exercise.share.StickerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 운동 기록을 이미지로 공유하는 헬퍼 클래스
 *
 * 공유 이미지 생성 과정:
 * 1. 배경 이미지 로드 (운동 기록의 첫 번째 사진)
 * 2. 스티커 생성 (운동 시간, 정보 등)
 * 3. 배경 위에 스티커 합성
 * 4. 인스타그램 호환 비율로 조정 (여백 추가)
 * 5. 시스템 공유 인텐트 실행
 */
object ExerciseShareHelper {
    private const val SHARE_IMAGE_FILE_NAME = "exercise_share.jpg"
    private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

    // 스티커 크기: 배경 이미지 너비의 45%
    private const val DEFAULT_STICKER_WIDTH_RATIO = 0.45f

    // 인스타그램 지원 비율
    // 세로: 4:5 (0.8), 가로: 1.91:1
    private const val INSTAGRAM_PORTRAIT_RATIO = 4f / 5f
    private const val INSTAGRAM_LANDSCAPE_RATIO = 1.91f

    // 공유 이미지 최대 크기 (성능 최적화)
    private const val MAX_IMAGE_DIMENSION = 1080

    suspend fun shareExerciseRecord(
        context: Context,
        record: ExerciseRecord,
        theme: StickerTheme = StickerTheme.DARK,
        transformInfo: StickerTransformInfo? = null,
    ) {
        val backgroundImageUrl = record.pictures?.firstOrNull()?.url

        // 배경 이미지와 스티커를 병렬로 준비
        val backgroundBitmap =
            withContext(Dispatchers.IO) {
                backgroundImageUrl?.let { loadBitmapFromUrl(context, it) }
            }

        val stickerBitmap =
            withContext(Dispatchers.Main) {
                ExerciseRecordStickerGenerator.generateStickerBitmap(context, record, theme)
            }

        val imageUri =
            withContext(Dispatchers.IO) {
                val combinedBitmap = createCombinedImage(stickerBitmap, backgroundBitmap, transformInfo)
                val instagramReadyBitmap = adjustToInstagramRatio(combinedBitmap)
                saveBitmapToCache(context, instagramReadyBitmap)
            }

        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        context.startActivity(Intent.createChooser(intent, null))
    }

    /**
     * 배경 이미지 위에 스티커를 합성
     * 스티커는 좌상단에 배치되며, 배경 너비에 비례한 크기로 스케일링됨
     */
    private fun createCombinedImage(
        stickerBitmap: Bitmap,
        backgroundBitmap: Bitmap?,
        transformInfo: StickerTransformInfo?,
    ): Bitmap {
        if (backgroundBitmap == null) {
            return stickerBitmap
        }

        val resultBitmap = backgroundBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        val widthRatio = transformInfo?.stickerWidthRatio ?: DEFAULT_STICKER_WIDTH_RATIO
        val targetStickerWidth = backgroundBitmap.width * widthRatio
        val finalScale = targetStickerWidth / stickerBitmap.width

        val scaledStickerWidth = (stickerBitmap.width * finalScale).toInt().coerceAtLeast(1)
        val scaledStickerHeight = (stickerBitmap.height * finalScale).toInt().coerceAtLeast(1)

        val scaledSticker = stickerBitmap.scale(scaledStickerWidth, scaledStickerHeight)

        val posX = (transformInfo?.translationXRatio ?: 0f) * backgroundBitmap.width
        val posY = (transformInfo?.translationYRatio ?: 0f) * backgroundBitmap.height

        canvas.drawBitmap(scaledSticker, posX, posY, Paint(Paint.FILTER_BITMAP_FLAG))

        scaledSticker.recycle()
        return resultBitmap
    }

    /**
     * 인스타그램 호환 비율로 이미지 조정
     *
     * 인스타그램 지원 비율:
     * - 정사각형: 1:1
     * - 세로: 4:5 (최대)
     * - 가로: 1.91:1 (최대)
     *
     * 비율을 벗어나는 이미지는 검정 여백을 추가하여 조정
     */
    private fun adjustToInstagramRatio(source: Bitmap): Bitmap {
        val sourceWidth = source.width.toFloat()
        val sourceHeight = source.height.toFloat()
        val sourceRatio = sourceWidth / sourceHeight

        val isPortrait = sourceHeight >= sourceWidth

        val targetRatio =
            if (isPortrait) {
                INSTAGRAM_PORTRAIT_RATIO
            } else {
                INSTAGRAM_LANDSCAPE_RATIO
            }

        // 이미 인스타그램 호환 비율인지 확인
        val isWithinInstagramBounds =
            if (isPortrait) {
                sourceRatio >= INSTAGRAM_PORTRAIT_RATIO
            } else {
                sourceRatio <= INSTAGRAM_LANDSCAPE_RATIO
            }

        if (isWithinInstagramBounds) {
            return source
        }

        // 새 캔버스 크기 계산
        // 세로 이미지인 경우 너비는 동일하게, 높이는 비율에 맞게 조정
        val newWidth: Int
        val newHeight: Int

        if (isPortrait) {
            newWidth = sourceWidth.toInt()
            newHeight = (sourceWidth / targetRatio).toInt()
        } else {
            newWidth = (sourceHeight * targetRatio).toInt()
            newHeight = sourceHeight.toInt()
        }

        val resultBitmap = createBitmap(newWidth, newHeight)
        val canvas = Canvas(resultBitmap)

        canvas.drawColor(Color.BLACK)

        canvas.drawBitmap(source, 0f, 0f, null)

        if (source !== resultBitmap) {
            source.recycle()
        }

        return resultBitmap
    }

    /**
     * URL에서 이미지 로드
     * 성능 최적화를 위해 최대 크기 제한 적용
     */
    private fun loadBitmapFromUrl(
        context: Context,
        url: String,
    ): Bitmap? {
        return try {
            Glide.with(context)
                .asBitmap()
                .load(url)
                .override(MAX_IMAGE_DIMENSION)
                .submit()
                .get()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 비트맵을 캐시에 저장하고 공유 가능한 URI 반환
     * JPEG 형식으로 저장하여 파일 크기 및 저장 속도 최적화
     */
    private fun saveBitmapToCache(
        context: Context,
        bitmap: Bitmap,
    ): Uri {
        val cacheDir = File(context.cacheDir, "share")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val file = File(cacheDir, SHARE_IMAGE_FILE_NAME)
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        }

        return FileProvider.getUriForFile(
            context,
            context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX,
            file,
        )
    }
}
