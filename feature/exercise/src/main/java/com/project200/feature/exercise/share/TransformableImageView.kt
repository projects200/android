package com.project200.feature.exercise.share

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class TransformableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val transformMatrix = Matrix()
    private val savedMatrix = Matrix()

    private var mode = NONE
    private val startPoint = PointF()
    private val midPoint = PointF()

    private var currentScale = 1f
    private var savedScale = 1f
    private var initialScale = 1f
    private val minScaleRatio = 0.3f
    private val maxScaleRatio = 3.0f

    private var translationX = 0f
    private var translationY = 0f

    private var isInitialized = false
    private val defaultWidthRatio = 0.45f
    private val defaultMarginRatio = 0.05f

    private val scaleDetector = ScaleGestureDetector(
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
        }
    )

    private var onTransformChangedListener: OnTransformChangedListener? = null

    interface OnTransformChangedListener {
        fun onTransformChanged(translationXRatio: Float, translationYRatio: Float, scale: Float)
    }

    fun setOnTransformChangedListener(listener: OnTransformChangedListener) {
        onTransformChangedListener = listener
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        isInitialized = false
        super.setImageBitmap(bm)
        if (bm != null && width > 0 && height > 0) {
            initializeTransform()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (drawable != null && !isInitialized && w > 0 && h > 0) {
            initializeTransform()
        }
    }

    private fun initializeTransform() {
        val drawableWidth = drawable?.intrinsicWidth ?: return
        if (drawableWidth <= 0) return

        val parentView = parent as? android.view.View ?: return
        val parentWidth = parentView.width.toFloat()
        val parentHeight = parentView.height.toFloat()
        if (parentWidth <= 0 || parentHeight <= 0) return

        val targetWidth = parentWidth * defaultWidthRatio
        initialScale = targetWidth / drawableWidth
        currentScale = initialScale
        savedScale = initialScale

        translationX = parentWidth * defaultMarginRatio
        translationY = parentHeight * defaultMarginRatio

        scaleType = ScaleType.MATRIX
        applyTransform()
        isInitialized = true
    }

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
                    midPoint.set(
                        (event.getX(0) + event.getX(1)) / 2,
                        (event.getY(0) + event.getY(1)) / 2
                    )
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
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
                notifyTransformChanged()
            }
        }

        return true
    }

    private fun applyTransform() {
        transformMatrix.reset()
        transformMatrix.postScale(currentScale, currentScale)
        transformMatrix.postTranslate(translationX, translationY)
        imageMatrix = transformMatrix
        scaleType = ScaleType.MATRIX
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
            stickerWidthRatio = widthRatio
        )
    }

    fun setInitialTransform(xRatio: Float, yRatio: Float, scale: Float) {
        post {
            val parent = parent as? android.view.View ?: return@post
            translationX = xRatio * parent.width
            translationY = yRatio * parent.height
            currentScale = scale
            savedScale = scale
            applyTransform()
        }
    }

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
}

data class StickerTransformInfo(
    val translationXRatio: Float,
    val translationYRatio: Float,
    val stickerWidthRatio: Float
)
