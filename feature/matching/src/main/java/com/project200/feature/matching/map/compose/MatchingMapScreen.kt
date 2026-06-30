package com.project200.feature.matching.map.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kakao.vectormap.label.Label
import com.project200.domain.model.MatchingMember
import com.project200.feature.matching.map.MapViewManager
import com.project200.feature.matching.map.MatchingMapViewModel
import com.project200.feature.matching.map.cluster.ClusterCalculator
import com.project200.feature.matching.map.cluster.MapClusterItem

/**
 * 매칭 지도 화면
 *
 * KakaoMapView 로 지도를 호스팅하고, ViewModel 의 combinedMapData 를 구독해 마커/클러스터를 그린다.
 *
 * @param onClusterClick 클러스터 라벨 클릭 시, 묶인 멤버 목록 전달
 * @param onPlaceMarkerClick 내 장소 마커 클릭 시
 */
@Composable
fun MatchingMapScreen(
    viewModel: MatchingMapViewModel,
    modifier: Modifier = Modifier,
    onClusterClick: (List<MapClusterItem>) -> Unit = {},
    onPlaceMarkerClick: () -> Unit = {},
) {
    val context = LocalContext.current
    // 멤버 데이터 구독
    val mapData by viewModel.combinedMapData.collectAsStateWithLifecycle()

    // 클러스터 계산기
    val clusterCalculator = remember { ClusterCalculator<MapClusterItem>() }
    var manager by remember { mutableStateOf<MapViewManager?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        KakaoMapView(
            modifier = Modifier.fillMaxSize(),
            onMapReady = { map ->
                manager =
                    MapViewManager(
                        context = context,
                        kakaoMap = map,
                        onCameraIdle = { cameraPosition ->
                            // 카메라 이동 종료 → 위치 저장 + 영역 이동 시 멤버 재조회
                            viewModel.saveLastLocation(
                                cameraPosition.position.latitude,
                                cameraPosition.position.longitude,
                                cameraPosition.zoomLevel,
                            )
                            manager?.getCurrentBounds()?.let { bounds ->
                                viewModel.fetchMatchingMembersIfMoved(
                                    currentBounds = bounds,
                                    currentCenter = cameraPosition.position,
                                    currentZoom = cameraPosition.zoomLevel,
                                )
                            }
                        },
                        onLabelClick = { label ->
                            handleLabelClick(label, manager, clusterCalculator, onClusterClick, onPlaceMarkerClick)
                        },
                    )
            },
        )
        // TODO: 필터, 현재위치 버튼 등 오버레이 여기에 추가
    }

    // 데이터(또는 지도 준비) 변경 시 마커/클러스터 다시 그리기
    LaunchedEffect(mapData, manager) {
        val mgr = manager ?: return@LaunchedEffect
        val (members, places) = mapData
        updateClusterData(members, clusterCalculator)
        mgr.redrawMarkers(places, clusterCalculator)
    }
}

/** ViewModel 의 최신 멤버를 ClusterCalculator 에 반영. */
private fun updateClusterData(
    members: List<MatchingMember>,
    clusterCalculator: ClusterCalculator<MapClusterItem>,
) {
    val clusterItems =
        members.flatMap { member ->
            member.locations.map { location -> MapClusterItem(member, location) }
        }
    clusterCalculator.clearItems()
    clusterCalculator.addItems(clusterItems)
}

/** 클릭한 라벨이 클러스터면 멤버 목록 콜백, 아니면 내 장소 마커 콜백. */
private fun handleLabelClick(
    label: Label,
    manager: MapViewManager?,
    clusterCalculator: ClusterCalculator<MapClusterItem>,
    onClusterClick: (List<MapClusterItem>) -> Unit,
    onPlaceMarkerClick: () -> Unit,
) {
    val cameraPosition = manager?.getCurrentCameraPosition() ?: return
    val cluster =
        clusterCalculator.getClusters(cameraPosition).find { cluster ->
            cluster.position.latitude == label.position.latitude &&
                cluster.position.longitude == label.position.longitude
        }
    if (cluster != null) {
        onClusterClick(cluster.items.toList())
    } else {
        onPlaceMarkerClick()
    }
}
