package com.project200.feature.matching.map

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.project200.feature.matching.map.cluster.MapClusterItem
import kotlin.math.ceil

/** 실제 SDK 배경 라벨의 지도 로컬 경계입니다. 화면 원점이나 터치 동작을 만들지 않습니다. */
data class MatchingMapTarget(
    val members: Collection<MapClusterItem>,
    val bounds: IntRect,
) {
    companion object {
        internal fun project(
            members: Collection<MapClusterItem>,
            point: IntOffset?,
            sizePx: Float,
            viewport: IntRect,
            mapSize: IntSize,
        ): MatchingMapTarget? {
            if (point == null || members.isEmpty() || !sizePx.isFinite() || sizePx <= 0f) return null
            if (viewport.width <= 0 || viewport.height <= 0 || mapSize.width <= 0 || mapSize.height <= 0) return null
            // 현재 호스트에서 검증된 원점·크기만 사용합니다. 다른 viewport의 좌표 원점은 추측하지 않습니다.
            if (
                viewport.left != 0 || viewport.top != 0 ||
                viewport.right != mapSize.width || viewport.bottom != mapSize.height
            ) {
                return null
            }
            val diameter = ceil(sizePx.toDouble()).toLong()
            if (diameter > Int.MAX_VALUE) return null
            val left = point.x.toLong() - diameter / 2
            val top = point.y.toLong() - diameter / 2
            val right = left + diameter
            val bottom = top + diameter
            // 잘린 노드의 중앙은 실제 라벨 중앙과 달라지므로 전체 경계가 보일 때만 노출합니다.
            if (
                left < maxOf(0, viewport.left) || top < maxOf(0, viewport.top) ||
                right > minOf(mapSize.width, viewport.right) || bottom > minOf(mapSize.height, viewport.bottom)
            ) {
                return null
            }
            return MatchingMapTarget(members, IntRect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt()))
        }
    }
}
