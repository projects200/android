package com.project200.feature.matching.map

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.project200.feature.matching.map.cluster.MapClusterItem
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchingMapTargetTest {
    private val members = listOf(mockk<MapClusterItem>())
    private val viewport = IntRect(0, 0, 300, 500)
    private val mapSize = IntSize(300, 500)

    @Test
    fun `실제 투영 중심과 비트맵 크기로 터치 경계를 만든다`() {
        val target = MatchingMapTarget.project(members, IntOffset(150, 250), 40f, viewport, mapSize)

        assertEquals(IntRect(130, 230, 170, 270), target?.bounds)
        assertEquals(IntOffset(150, 250), target?.bounds?.center)
    }

    @Test
    fun `없는 투영과 지도 밖 투영은 타깃을 만들지 않는다`() {
        for (point in listOf(null, IntOffset(-1, 250), IntOffset(301, 250), IntOffset(150, 501))) {
            assertNull(MatchingMapTarget.project(members, point, 40f, viewport, mapSize))
        }
    }

    @Test
    fun `일부 잘린 마커를 경계 중앙이 다른 타깃으로 바꾸지 않는다`() {
        for (point in listOf(IntOffset(10, 250), IntOffset(290, 250), IntOffset(150, 10), IntOffset(150, 490))) {
            assertNull(MatchingMapTarget.project(members, point, 40f, viewport, mapSize))
        }
        assertNull(MatchingMapTarget.project(members, IntOffset(150, 250), 40f, viewport, IntSize(100, 100)))
    }

    @Test
    fun `유효하지 않은 크기와 비어 있는 viewport는 타깃을 만들지 않는다`() {
        for (size in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.MAX_VALUE)) {
            assertNull(MatchingMapTarget.project(members, IntOffset(150, 250), size, viewport, mapSize))
        }
        assertNull(MatchingMapTarget.project(members, IntOffset(150, 250), 40f, IntRect.Zero, mapSize))
        assertNull(MatchingMapTarget.project(members, IntOffset(150, 250), 40f, viewport, IntSize.Zero))
    }

    @Test
    fun `원점이나 크기가 다른 viewport는 추측 변환 없이 거절한다`() {
        for (other in listOf(IntRect(20, 30, 280, 470), IntRect(0, 0, 280, 470))) {
            assertNull(MatchingMapTarget.project(members, IntOffset(150, 250), 40f, other, mapSize))
        }
    }
}
