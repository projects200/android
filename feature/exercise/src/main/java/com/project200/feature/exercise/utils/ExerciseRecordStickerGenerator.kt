package com.project200.feature.exercise.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import com.project200.domain.model.ExerciseRecord
import com.project200.undabang.feature.exercise.R
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

object ExerciseRecordStickerGenerator {
    private const val CORNER_RADIUS_DP = 20f

    fun generateStickerBitmap(
        context: Context,
        record: ExerciseRecord,
    ): Bitmap {
        val inflater = LayoutInflater.from(context)
        val stickerView = inflater.inflate(R.layout.layout_exercise_sticker, null, false)

        bindRecordToView(stickerView, record)

        val rawBitmap = convertViewToBitmap(stickerView)
        return applyRoundedClipping(context, rawBitmap)
    }

    private fun bindRecordToView(
        view: View,
        record: ExerciseRecord,
    ) {
        val timeValue = view.findViewById<TextView>(R.id.sticker_time_value)
        timeValue.text = formatDuration(record.startedAt, record.endedAt)

        val exerciseInfo = view.findViewById<TextView>(R.id.sticker_exercise_info)
        val dayOfWeek = record.startedAt.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        exerciseInfo.text = "$dayOfWeek ${record.title}"

        val typeText = view.findViewById<TextView>(R.id.sticker_type_text)
        val typeIcon = view.findViewById<ImageView>(R.id.sticker_type_icon)
        if (record.personalType.isNotBlank()) {
            typeText.text = record.personalType
            typeText.visibility = View.VISIBLE
            typeIcon.visibility = View.VISIBLE
        } else {
            typeText.visibility = View.GONE
            typeIcon.visibility = View.GONE
        }

        val locationText = view.findViewById<TextView>(R.id.sticker_location_text)
        val locationIcon = view.findViewById<ImageView>(R.id.sticker_location_icon)
        if (record.location.isNotBlank()) {
            locationText.text = record.location
            locationText.visibility = View.VISIBLE
            locationIcon.visibility = View.VISIBLE
        } else {
            locationText.visibility = View.GONE
            locationIcon.visibility = View.GONE
        }
    }

    private fun formatDuration(
        startedAt: LocalDateTime,
        endedAt: LocalDateTime,
    ): String {
        val duration = Duration.between(startedAt, endedAt)
        val totalMinutes = duration.toMinutes()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return String.format(Locale.getDefault(), "%02d:%02d", hours, minutes)
    }

    private fun convertViewToBitmap(view: View): Bitmap {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )

        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val bitmap = createBitmap(view.measuredWidth, view.measuredHeight)

        val canvas = Canvas(bitmap)
        view.draw(canvas)

        return bitmap
    }

    private fun applyRoundedClipping(
        context: Context,
        source: Bitmap,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val cornerRadius = CORNER_RADIUS_DP * density

        val width = source.width
        val height = source.height

        val output = createBitmap(width, height)
        val canvas = Canvas(output)

        val clipPath =
            Path().apply {
                addRoundRect(
                    RectF(0f, 0f, width.toFloat(), height.toFloat()),
                    cornerRadius,
                    cornerRadius,
                    Path.Direction.CW,
                )
            }

        canvas.withClip(clipPath) {
            drawBitmap(source, 0f, 0f, null)
        }

        source.recycle()
        return output
    }
}
