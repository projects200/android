package com.project200.feature.exercise.share

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import com.project200.feature.exercise.utils.StickerTransformInfo

/**
 * 드래그, 핀치줌, 회전을 지원하는 커스텀 ImageView
 *
 * 스티커 편집 화면에서 사용자가 스티커의 위치, 크기, 회전을 조정할 수 있도록 함.
 *
 * 초기화 동작:
 * 1. 이미지가 설정되면 부모 뷰 너비의 45% 크기로 자동 스케일링
 * 2. 좌상단에서 5% 마진 위치에 배치
 * 3. ScaleType.MATRIX 모드로 전환하여 직접 변환 제어
 *
 * 터치 제스처:
 * - 한 손가락 드래그: 위치 이동
 * - 두 손가락 핀치: 크기 조절 (초기 크기의 30% ~ 300%)
 * - 두 손가락 회전: 회전 조절
 *
 * @see StickerTransformInfo 현재 변환 정보를 담는 data class
 * @see ExerciseShareHelper 이 뷰의 변환 정보를 사용하여 최종 이미지 생성
 */
class TransformableImageView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : AppCompatImageView(context, attrs, defStyleAttr) {
        private val transformMatrix = Matrix()
        private val savedMatrix = Matrix()

        // 터치 모드 상태
        private var mode = NONE
        private val startPoint = PointF()
        private val midPoint = PointF()

        // 스케일 관련 변수
        private var currentScale = 1f
        private var savedScale = 1f
        private var initialScale = 1f

        // 스케일 제한: 초기 크기 대비 30% ~ 300%
        private val minScaleRatio = 0.3f
        private val maxScaleRatio = 3.0f

        // 위치 (픽셀 단위)
        private var translationX = 0f
        private var translationY = 0f

        // 회전 (도 단위)
        private var currentRotation = 0f
        private var savedRotation = 0f
        private var startAngle = 0f

        // 초기화 상태
        private var isInitialized = false
        private var hasUserInteracted = false
        private var pendingTransform: StickerTransformInfo? = null

        // 기본 설정: 부모 너비의 45% 크기, 5% 마진
        private val defaultWidthRatio = 0.45f
        private val defaultMarginRatio = 0.05f

        /**
         * 핀치줌 제스처 감지기
         * 두 손가락으로 확대/축소 시 스케일 값을 조정
         */
        private val scaleDetector =
            ScaleGestureDetector(
                context,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                        savedScale = currentScale
                        return true
                    }

                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val scaleFactor = detector.scaleFactor
                        val newScale = currentScale * scaleFactor

                        val minScale = initialScale * minScaleRatio
                        val maxScale = initialScale * maxScaleRatio

                        currentScale = newScale.coerceIn(minScale, maxScale)
                        applyTransform()
                        return true
                    }
                },
            )

        private var onTransformChangedListener: OnTransformChangedListener? = null

        interface OnTransformChangedListener {
            fun onTransformChanged(
                translationXRatio: Float,
                translationYRatio: Float,
                scale: Float,
            )
        }

        fun setOnTransformChangedListener(listener: OnTransformChangedListener) {
            onTransformChangedListener = listener
        }

        /**
         * 비트맵 설정 시 초기화 플래그 리셋 후 변환 초기화 시도
         */
        override fun setImageBitmap(bm: android.graphics.Bitmap?) {
            isInitialized = false
            super.setImageBitmap(bm)
            if (bm != null && width > 0 && height > 0) {
                initializeTransform()
            }
        }

        /**
         * 뷰 크기 변경 시 초기화되지 않은 상태면 변환 초기화
         */
        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (drawable != null && !isInitialized && w > 0 && h > 0) {
                initializeTransform()
            }
        }

        /**
         * 초기 변환 설정
         * - 스케일: 부모 너비의 45%가 되도록 계산
         * - 위치: 좌상단에서 5% 마진
         * - pendingTransform이 있으면 해당 값으로 복원
         */
        private fun initializeTransform() {
            val drawableWidth = drawable?.intrinsicWidth ?: return
            if (drawableWidth <= 0) return

            val parentView = parent as? android.view.View ?: return
            val parentWidth = parentView.width.toFloat()
            val parentHeight = parentView.height.toFloat()
            if (parentWidth <= 0 || parentHeight <= 0) return

            val pending = pendingTransform
            if (pending != null) {
                val targetStickerWidth = parentWidth * pending.stickerWidthRatio
                currentScale = targetStickerWidth / drawableWidth
                savedScale = currentScale
                initialScale = parentWidth * defaultWidthRatio / drawableWidth

                translationX = pending.translationXRatio * parentWidth
                translationY = pending.translationYRatio * parentHeight
                currentRotation = pending.rotationDegrees
                savedRotation = currentRotation

                pendingTransform = null
            } else {
                val targetWidth = parentWidth * defaultWidthRatio
                initialScale = targetWidth / drawableWidth
                currentScale = initialScale
                savedScale = initialScale

                translationX = parentWidth * defaultMarginRatio
                translationY = parentHeight * defaultMarginRatio
                currentRotation = 0f
                savedRotation = 0f
            }

            scaleType = ScaleType.MATRIX
            applyTransform()
            isInitialized = true
        }

        /**
         * 터치 이벤트 처리
         * - ACTION_DOWN: 드래그 시작점 저장
         * - ACTION_POINTER_DOWN: 핀치줌 모드 전환
         * - ACTION_MOVE: 드래그 시 위치 업데이트
         * - ACTION_UP: 모드 리셋 및 리스너 콜백
         */
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            scaleDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    savedMatrix.set(transformMatrix)
                    startPoint.set(event.x, event.y)
                    mode = DRAG
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2) {
                        savedScale = currentScale
                        savedRotation = currentRotation
                        midPoint.set(
                            (event.getX(0) + event.getX(1)) / 2,
                            (event.getY(0) + event.getY(1)) / 2,
                        )
                        startAngle = calculateAngle(event)
                        mode = ZOOM
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (mode == DRAG && event.pointerCount == 1) {
                        val dx = event.x - startPoint.x
                        val dy = event.y - startPoint.y

                        translationX += dx
                        translationY += dy

                        startPoint.set(event.x, event.y)
                        applyTransform()
                    } else if (mode == ZOOM && event.pointerCount == 2) {
                        val currentAngle = calculateAngle(event)
                        currentRotation = savedRotation + (currentAngle - startAngle)
                        applyTransform()
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    mode = NONE
                    hasUserInteracted = true
                    notifyTransformChanged()
                }
            }

            return true
        }

        /**
         * 현재 스케일, 회전, 위치를 Matrix에 적용
         * 회전은 이미지 중심을 기준으로 적용
         */
        private fun applyTransform() {
            val drawableWidth = drawable?.intrinsicWidth?.toFloat() ?: 0f
            val drawableHeight = drawable?.intrinsicHeight?.toFloat() ?: 0f

            val scaledCenterX = (drawableWidth * currentScale) / 2
            val scaledCenterY = (drawableHeight * currentScale) / 2

            transformMatrix.reset()
            transformMatrix.postScale(currentScale, currentScale)
            transformMatrix.postRotate(currentRotation, scaledCenterX, scaledCenterY)
            transformMatrix.postTranslate(translationX, translationY)
            imageMatrix = transformMatrix
            scaleType = ScaleType.MATRIX
        }

        private fun calculateAngle(event: MotionEvent): Float {
            val dx = event.getX(1) - event.getX(0)
            val dy = event.getY(1) - event.getY(0)
            return Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        }

        private fun notifyTransformChanged() {
            val parent = parent as? android.view.View ?: return
            val parentWidth = parent.width.toFloat()
            val parentHeight = parent.height.toFloat()

            if (parentWidth > 0 && parentHeight > 0) {
                val xRatio = translationX / parentWidth
                val yRatio = translationY / parentHeight
                onTransformChangedListener?.onTransformChanged(xRatio, yRatio, currentScale)
            }
        }

        /**
         * 현재 변환 정보를 비율 기반으로 반환
         * ExerciseShareHelper에서 최종 이미지 생성 시 사용
         *
         * @return 위치(비율)와 스티커 너비 비율을 담은 StickerTransformInfo
         */
        fun getTransformInfo(): StickerTransformInfo {
            val parent = parent as? android.view.View
            val parentWidth = parent?.width?.toFloat() ?: 1f
            val parentHeight = parent?.height?.toFloat() ?: 1f
            val drawableWidth = drawable?.intrinsicWidth?.toFloat() ?: 1f

            val stickerVisualWidth = drawableWidth * currentScale
            val widthRatio = if (parentWidth > 0) stickerVisualWidth / parentWidth else defaultWidthRatio

            return StickerTransformInfo(
                translationXRatio = if (parentWidth > 0) translationX / parentWidth else 0f,
                translationYRatio = if (parentHeight > 0) translationY / parentHeight else 0f,
                stickerWidthRatio = widthRatio,
                rotationDegrees = currentRotation,
            )
        }

        /**
         * 외부에서 변환 값을 직접 설정
         * 저장된 상태 복원 시 사용 가능
         */
        fun setInitialTransform(
            xRatio: Float,
            yRatio: Float,
            scale: Float,
        ) {
            post {
                val parent = parent as? android.view.View ?: return@post
                translationX = xRatio * parent.width
                translationY = yRatio * parent.height
                currentScale = scale
                savedScale = scale
                applyTransform()
            }
        }

        /**
         * 사용자가 드래그/핀치줌으로 조작했는지 여부
         */
        fun hasUserInteracted(): Boolean = hasUserInteracted

        /**
         * 비트맵 설정 전에 호출하여 초기화 시 해당 위치로 바로 설정
         */
        fun setPendingTransform(transformInfo: StickerTransformInfo) {
            pendingTransform = transformInfo
        }

        companion object {
            private const val NONE = 0
            private const val DRAG = 1
            private const val ZOOM = 2
        }
    }
