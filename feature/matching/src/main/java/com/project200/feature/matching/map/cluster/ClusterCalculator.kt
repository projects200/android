package com.project200.feature.matching.map.cluster

import com.kakao.vectormap.camera.CameraPosition
import ted.gun0912.clustering.clustering.Cluster
import ted.gun0912.clustering.clustering.TedClusterItem
import ted.gun0912.clustering.clustering.algo.NonHierarchicalDistanceBasedAlgorithm
import ted.gun0912.clustering.clustering.algo.ScreenBasedAlgorithmAdapter
import ted.gun0912.clustering.geometry.TedCameraPosition
import ted.gun0912.clustering.geometry.TedLatLng

/**
 * tedclustering 라이브러리의 클러스터링 알고리즘만 사용하는 계산기 클래스
 */
class ClusterCalculator<T : TedClusterItem> {
    // 라이브러리의 핵심 알고리즘 객체
    private val algorithm = ScreenBasedAlgorithmAdapter<T>(NonHierarchicalDistanceBasedAlgorithm())

    /**
     * 클러스터링할 아이템들을 추가합니다.
     */
    fun addItems(items: Collection<T>) {
        algorithm.addItems(items)
    }

    /**
     * 모든 아이템을 제거합니다.
     */
    fun clearItems() {
        algorithm.clearItems()
    }

    /**
     * 현재 지도 상태를 기준으로 클러스터들을 계산하여 반환합니다.
     * @param cameraPosition 현재 카카오맵의 카메라 위치 정보
     * @return 계산된 Cluster 집합
     */
    fun getClusters(cameraPosition: CameraPosition): Set<Cluster<T>> {
        // 카카오맵의 CameraPosition을 라이브러리의 TedCameraPosition으로 변환
        val tedCameraPosition =
            TedCameraPosition(
                TedLatLng(cameraPosition.position.latitude, cameraPosition.position.longitude),
                cameraPosition.zoomLevel.toDouble(),
                tilt = cameraPosition.tiltAngle.toDouble(),
                bearing = cameraPosition.rotationAngle.toDouble(),
            )
        algorithm.onCameraChange(tedCameraPosition)
        return algorithm.getClusters(cameraPosition.zoomLevel.toDouble())
    }
}
