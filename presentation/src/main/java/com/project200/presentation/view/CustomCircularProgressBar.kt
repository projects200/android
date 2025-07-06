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
import androidx.core.content.withStyledAttributes
import com.project200.common.constants.RuleConstants.SCORE_HIGH_LEVEL
import com.project200.common.constants.RuleConstants.SCORE_MIDDLE_LEVEL
import com.project200.undabang.presentation.R

class CustomCircularProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 진행도 링을 그리는 Paint
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND // 진행 바의 끝을 둥글게
        color = ContextCompat.getColor(context, R.color.main) // 기본 진행 색상
    }

    private val rectF = RectF() // 원형을 그릴 영역

    // onDraw에서 사용되는 애니메이션 점수
    private var _animatedScore: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    // score (0-100) 값
    var score: Int = 0
        set(value) {
            field = value.coerceIn(0, 100) // 0-100 범위로 제한
            updateProgressAndColor() // 점수 변경 시 진행도와 색상 업데이트

            animateProgress(0, field)
        }

    // 프로그레스 바의 두께
    var strokeWidth: Float = resources.displayMetrics.density * 10 // 기본 두께 10dp
        set(value) {
            field = value
            progressPaint.strokeWidth = field
            invalidate()
        }

    init {
        context.withStyledAttributes(
            attrs,
            R.styleable.CustomCircularProgressBar,
            defStyleAttr,
            0
        ) {
            score = getInt(R.styleable.CustomCircularProgressBar_score, 0) // 기본값 0으로 시작
            strokeWidth = getDimension(R.styleable.CustomCircularProgressBar_strokeWidth, strokeWidth)
        }

        // 초기 Paint 색상 및 두께 설정
        progressPaint.strokeWidth = strokeWidth
        updateProgressAndColor() // 초기 점수에 따른 색상 설정 (init에서 설정된 score를 기반)
    }

    // 뷰의 크기가 변경될 때마다 원형 그릴 영역을 업데이트
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val halfStroke = strokeWidth / 2f
        rectF.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke)
    }

    // 실제로 뷰를 그리는 메서드
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 진행도 링 그리기
        val maxProgressAngle = 360f * 0.95f // 100점이어도 95%까지만 차도록 (360 * 0.95 = 342도)
        val currentProgressAngle = (_animatedScore / 100f) * maxProgressAngle // 점수를 0-100% 비율로 변환 후, 최대 진행 각도에 매핑
        val startAngle = 270f // 시작점 12시 방향

        canvas.drawArc(rectF, startAngle, currentProgressAngle, false, progressPaint)
    }

    // 점수에 따라 진행도와 색상을 업데이트
    private fun updateProgressAndColor() {
        progressPaint.color = when {
            score >= SCORE_HIGH_LEVEL -> ContextCompat.getColor(context, R.color.score_high_level)
            score >= SCORE_MIDDLE_LEVEL -> ContextCompat.getColor(context, R.color.score_middle_level)
            else -> ContextCompat.getColor(context, R.color.score_low_level)
        }
    }

    // 진행도를 애니메이션하는 메서드
    private fun animateProgress(fromScore: Int, toScore: Int) {
        val animator = ValueAnimator.ofInt(fromScore, toScore)
        animator.duration = 500 // 애니메이션 지속 시간 (ms)
        animator.interpolator = DecelerateInterpolator() // 서서히 느려지는 애니메이션 (선택 사항)
        animator.addUpdateListener { animation ->
            _animatedScore = animation.animatedValue as Int
        }
        animator.start()
    }
}