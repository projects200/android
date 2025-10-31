package com.project200.feature.matching.map.cluster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.createBitmap
import com.project200.presentation.utils.UiUtils.dpToPx

object ClusterMarkerHelper {
    // 중간 크기 클러스터가 되는 개수
    private const val SMALL_CLUSTER_COUNT_THRESHOLD = 5
    // 큰 크기 클러스터가 되는 개수
    private const val MEDIUM_CLUSTER_COUNT_THRESHOLD = 10

    /**
     * 클러스터에 포함된 아이템 수에 따라 마커의 크기(px)를 결정합니다.
     * @param count 아이템 개수
     * @return 변환된 픽셀(px) 크기
     */
    fun getClusterSizePx(context: Context, count: Int): Int {
        // dp 값 (아이템 10개 미만은 40dp, 100개 미만은 50dp, 그 이상은 60dp)
        val dp = when {
            count < SMALL_CLUSTER_COUNT_THRESHOLD -> 40
            count < MEDIUM_CLUSTER_COUNT_THRESHOLD -> 50
            else -> 60
        }
        // 디바이스 밀도에 따라 dp를 px로 변환
        return dpToPx(context, dp.toFloat())
    }

    /**
     * 주어진 크기와 색상으로 원형 모양의 비트맵을 생성합니다.
     * @param sizePx 비트맵의 가로/세로 크기 (px)
     * @param markerColor 원의 채우기 색상
     * @param strokeColor 원의 테두리 색상
     * @return 생성된 비트맵
     */
    fun createClusterBitmap(sizePx: Int, markerColor: Int, strokeColor: Int): Bitmap {
        // 코드로 Drawable 생성 (XML의 <shape> 태그와 동일한 역할)
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL // 모양을 원으로 설정
            setColor(markerColor)               // 색상 설정
            setSize(sizePx, sizePx)       // 크기 설정
            setStroke(1,strokeColor) // 흰색 테두리 설정
        }

        // Drawable을 그릴 Bitmap과 Canvas 생성
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)

        // Canvas에 Drawable을 그립니다.
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }
}