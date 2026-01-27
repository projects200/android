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
import com.project200.feature.exercise.share.StickerTransformInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ExerciseShareHelper {
    private const val SHARE_IMAGE_FILE_NAME = "exercise_share.jpg"
    private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

    private const val DEFAULT_STICKER_WIDTH_RATIO = 0.45f
    private const val INSTAGRAM_PORTRAIT_RATIO = 4f / 5f
    private const val INSTAGRAM_LANDSCAPE_RATIO = 1.91f
    private const val MAX_IMAGE_DIMENSION = 1080

    suspend fun shareExerciseRecord(
        context: Context,
        record: ExerciseRecord,
        theme: StickerTheme = StickerTheme.DARK,
        transformInfo: StickerTransformInfo? = null,
    ) {
        val backgroundImageUrl = record.pictures?.firstOrNull()?.url

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

        val isWithinInstagramBounds =
            if (isPortrait) {
                sourceRatio >= INSTAGRAM_PORTRAIT_RATIO
            } else {
                sourceRatio <= INSTAGRAM_LANDSCAPE_RATIO
            }

        if (isWithinInstagramBounds) {
            return source
        }

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
