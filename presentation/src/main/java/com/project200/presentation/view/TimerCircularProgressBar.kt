package com.project200.presentation.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
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

    var visualProgress: Float = 1.0f


    private var progressAnimator: ValueAnimator? = null


        init {
            backgroundPaint.strokeWidth = strokeWidth
            progressPaint.strokeWidth = strokeWidth
        }

    /**
     * Fragment에서 이 함수를 호출하여 타이머 프로그레스바 진행률을 설정합니다.
     * @param progress 진행률 (0.0f ~ 1.0f)
     * @param animated true일 경우 애니메이션으로, false일 경우 즉시 반영됩니다.
     */
    fun setProgress(progress: Float, animated: Boolean = true) {
        // 이전에 실행 중이던 애니메이션이 있다면 무조건 취소
        progressAnimator?.cancel()

        if (animated) {
            // animated가 true일 경우에만 애니메이션 실행
            progressAnimator = ValueAnimator.ofFloat(visualProgress, progress).apply {
                duration = 150L
                interpolator = DecelerateInterpolator()
                addUpdateListener { animation ->
                    visualProgress = animation.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            // animated가 false일 경우, 즉시 값을 설정하고 뷰를 다시 그림
            visualProgress = progress
            invalidate()
        }
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


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawArc(rectF, 0f, 360f, false, backgroundPaint)
        canvas.drawArc(rectF, 270f, -visualProgress * 360f, false, progressPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        progressAnimator?.cancel()
    }
    }
