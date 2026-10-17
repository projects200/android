package com.project200.feature.matching.map

import com.google.common.truth.Truth.assertThat
import com.kakao.vectormap.label.Label
import com.project200.domain.model.ExercisePlace
import com.project200.feature.matching.map.cluster.MapClusterItem
import com.project200.feature.matching.map.compose.handleLabelClick
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import ted.gun0912.clustering.clustering.Cluster

class MatchingMapLabelClickTest {
    @Test
    fun `실제 렌더링한 클러스터 태그는 좌표 재계산 없이 같은 회원과 장소를 연다`() {
        val label = mockk<Label>()
        val item = mockk<MapClusterItem>()
        val cluster = mockk<Cluster<MapClusterItem>>()
        every { label.tag } returns cluster
        every { cluster.items } returns setOf(item)
        val onClusterClick = mockk<(List<MapClusterItem>) -> Unit>(relaxed = true)
        val onPlaceMarkerClick = mockk<() -> Unit>(relaxed = true)

        assertThat(handleLabelClick(label, onClusterClick, onPlaceMarkerClick)).isEqualTo(MatchingMapLabelKind.CLUSTER)

        verify(exactly = 1) { onClusterClick(listOf(item)) }
        verify(exactly = 0) { onPlaceMarkerClick() }
        verify(exactly = 0) { label.position }
    }

    @Test
    fun `내 장소 태그만 운동 장소 목록을 연다`() {
        val label = mockk<Label>()
        every { label.tag } returns ExercisePlace(1L, "내 장소", "실제 주소", 37.5, 127.0)
        val onClusterClick = mockk<(List<MapClusterItem>) -> Unit>(relaxed = true)
        val onPlaceMarkerClick = mockk<() -> Unit>(relaxed = true)

        assertThat(handleLabelClick(label, onClusterClick, onPlaceMarkerClick)).isEqualTo(MatchingMapLabelKind.PLACE)

        verify(exactly = 0) { onClusterClick(any()) }
        verify(exactly = 1) { onPlaceMarkerClick() }
    }

    @Test
    fun `알 수 없는 라벨을 내 장소로 추정하지 않는다`() {
        val label = mockk<Label>()
        val onClusterClick = mockk<(List<MapClusterItem>) -> Unit>(relaxed = true)
        val onPlaceMarkerClick = mockk<() -> Unit>(relaxed = true)
        for (tag in listOf(null, "unknown")) {
            every { label.tag } returns tag
            assertThat(handleLabelClick(label, onClusterClick, onPlaceMarkerClick)).isEqualTo(MatchingMapLabelKind.UNKNOWN)
        }
        verify(exactly = 0) { onClusterClick(any()) }
        verify(exactly = 0) { onPlaceMarkerClick() }
    }
}
