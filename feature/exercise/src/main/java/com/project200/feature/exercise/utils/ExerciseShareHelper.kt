package com.project200.feature.exercise.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.project200.domain.model.ExerciseRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ExerciseShareHelper {

    private const val SHARE_IMAGE_FILE_NAME = "exercise_share.png"
    private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"
    private const val STICKER_MARGIN_DP = 24f

    suspend fun shareExerciseRecord(
        context: Context,
        record: ExerciseRecord
    ) {
        val stickerBitmap = withContext(Dispatchers.Main) {
            ExerciseRecordStickerGenerator.generateStickerBitmap(context, record)
        }

        val backgroundImageUrl = record.pictures?.firstOrNull()?.url

        val imageUri = withContext(Dispatchers.IO) {
            val combinedBitmap = createCombinedImage(context, stickerBitmap, backgroundImageUrl)
            saveBitmapToCache(context, combinedBitmap, SHARE_IMAGE_FILE_NAME)
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, null))
    }

    private fun createCombinedImage(
        context: Context,
        stickerBitmap: Bitmap,
        backgroundImageUrl: String?
    ): Bitmap {
        val backgroundBitmap = backgroundImageUrl?.let { loadBitmapFromUrl(context, it) }

        return if (backgroundBitmap != null) {
            val resultBitmap = backgroundBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(resultBitmap)

            val density = context.resources.displayMetrics.density
            val margin = STICKER_MARGIN_DP * density

            canvas.drawBitmap(stickerBitmap, margin, margin, Paint())

            resultBitmap
        } else {
            stickerBitmap
        }
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

    private fun saveBitmapToCache(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val cacheDir = File(context.cacheDir, "share")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val file = File(cacheDir, fileName)
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
