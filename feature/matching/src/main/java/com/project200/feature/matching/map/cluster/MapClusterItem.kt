// 파일 경로: feature/matching/map/cluster/MapClusterItem.kt

package com.project200.feature.matching.map.cluster


import com.project200.domain.model.ExercisePlace
import com.project200.domain.model.Location
import com.project200.domain.model.MatchingMember
import ted.gun0912.clustering.clustering.TedClusterItem
import ted.gun0912.clustering.geometry.TedLatLng

/**
 * 지도에 표시될 모든 아이템이 구현해야 하는 공통 인터페이스.
 * TedClusterItem을 상속받아 클러스터링 라이브러리가 위치를 알 수 있게 합니다.
 */
sealed interface MapClusterItem : TedClusterItem {
    val position: TedLatLng
    override fun getTedLatLng(): TedLatLng = position
}

/**
 * MatchingMember 데이터를 감싸는 클러스터 아이템
 * @param member 원본 회원 데이터
 * @param location 회원의 여러 위치 중 지도에 표시할 특정 위치
 */
data class MemberClusterItem(
    val member: MatchingMember,
    val location: Location
) : MapClusterItem {
    // TedLatLng 타입으로 위치 정보를 변환
    override val position: TedLatLng = TedLatLng(location.latitude, location.longitude)
}

/**
 * ExercisePlace 데이터를 감싸는 클러스터 아이템
 * @param place 원본 장소 데이터
 */
data class PlaceClusterItem(
    val place: ExercisePlace
) : MapClusterItem {
    // TedLatLng 타입으로 위치 정보를 변환
    override val position: TedLatLng = TedLatLng(place.latitude, place.longitude)
}