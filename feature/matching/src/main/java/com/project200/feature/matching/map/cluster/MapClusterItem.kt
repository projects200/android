// 파일 경로: feature/matching/map/cluster/MapClusterItem.kt
package com.project200.feature.matching.map.cluster

import com.project200.domain.model.Location
import com.project200.domain.model.MatchingMember
import ted.gun0912.clustering.clustering.TedClusterItem
import ted.gun0912.clustering.geometry.TedLatLng

/**
 * MatchingMember 데이터를 감싸는 클러스터 아이템
 * TedClusterItem을 직접 구현하여 클러스터링 라이브러리가 사용할 수 있도록 합니다.
 *
 * @param member 원본 회원 데이터
 * @param location 회원의 여러 위치 중 지도에 표시할 특정 위치
 */
data class MapClusterItem(
    val member: MatchingMember,
    val location: Location,
) : TedClusterItem {
    override fun getTedLatLng(): TedLatLng {
        return TedLatLng(location.latitude, location.longitude)
    }
}
