package com.project200.feature.exercise.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.project200.domain.model.ExerciseRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.scale

object ExerciseShareHelper {

    private const val SHARE_IMAGE_FILE_NAME = "exercise_share.png"
    private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"
    private const val STICKER_WIDTH_RATIO = 0.45f
    private const val STICKER_MARGIN_RATIO = 0.03f

    suspend fun shareExerciseRecord(
        context: Context,
        record: ExerciseRecord
    ) {
        val backgroundImageUrl = record.pictures?.firstOrNull()?.url

        val backgroundBitmap = withContext(Dispatchers.IO) {
            backgroundImageUrl?.let { loadBitmapFromUrl(context, it) }
        }

        val stickerBitmap = withContext(Dispatchers.Main) {
            ExerciseRecordStickerGenerator.generateStickerBitmap(context, record)
        }

        val imageUri = withContext(Dispatchers.IO) {
            val combinedBitmap = createCombinedImage(stickerBitmap, backgroundBitmap)
            saveBitmapToCache(context, combinedBitmap)
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, null))
    }

    private fun createCombinedImage(
        stickerBitmap: Bitmap,
        backgroundBitmap: Bitmap?
    ): Bitmap {
        if (backgroundBitmap == null) {
            return stickerBitmap
        }

        val resultBitmap = backgroundBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        val targetStickerWidth = backgroundBitmap.width * STICKER_WIDTH_RATIO
        val scale = targetStickerWidth / stickerBitmap.width
        val scaledStickerWidth = (stickerBitmap.width * scale).toInt()
        val scaledStickerHeight = (stickerBitmap.height * scale).toInt()

        val scaledSticker = stickerBitmap.scale(scaledStickerWidth, scaledStickerHeight)

        val margin = backgroundBitmap.width * STICKER_MARGIN_RATIO

        canvas.drawBitmap(scaledSticker, margin, margin, Paint(Paint.FILTER_BITMAP_FLAG))

        scaledSticker.recycle()
        return resultBitmap
    }

    private fun loadBitmapFromUrl(context: Context, url: String): Bitmap? {
        return try {
            Glide.with(context)
                .asBitmap()
                .load(url)
                .submit()
                .get()
        } catch (e: Exception) {
            null
        }
    }

    private fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri {
        val cacheDir = File(context.cacheDir, "share")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val file = File(cacheDir, SHARE_IMAGE_FILE_NAME)
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        return FileProvider.getUriForFile(
            context,
            context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX,
            file
        )
    }
}
