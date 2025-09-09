package com.project200.presentation.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.project200.undabang.presentation.R

class TimerCircularProgressBar
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        // 배경 링을 그리는 Paint
        private val backgroundPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = ContextCompat.getColor(context, R.color.main_background)
            }

        // 진행도 링을 그리는 Paint
        private val progressPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                color = ContextCompat.getColor(context, R.color.main)
            }

        // 원형을 그릴 영역
        private val rectF = RectF()

        // 프로그레스 바의 두께
        var strokeWidth: Float = resources.displayMetrics.density * 20
            set(value) {
                field = value
                backgroundPaint.strokeWidth = field
                progressPaint.strokeWidth = field
                invalidate()
            }

        // 현재 진행률 (0.0f ~ 1.0f)
        var progress: Float = 1.0f
            set(value) {
                field = value
                invalidate()
            }

        init {
            backgroundPaint.strokeWidth = strokeWidth
            progressPaint.strokeWidth = strokeWidth
        }

        // 뷰의 크기가 변경될 때마다 원형 그릴 영역을 업데이트
        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            val halfStroke = strokeWidth / 2f
            rectF.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke)
        }

        // 실제로 뷰를 그리는 메서드
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            // 배경 링 그리기 (전체 원)
            canvas.drawArc(rectF, 0f, 360f, false, backgroundPaint)

            // 진행도 링 그리기 (진행도가 시계방향으로 감소)
            canvas.drawArc(rectF, 270f, -progress * 360f, false, progressPaint)
        }
    }
