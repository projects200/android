package com.project200.feature.matching.map.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kakao.vectormap.label.Label
import com.project200.domain.model.MatchingMember
import com.project200.feature.matching.map.MapViewManager
import com.project200.feature.matching.map.MatchingMapViewModel
import com.project200.feature.matching.map.cluster.ClusterCalculator
import com.project200.feature.matching.map.cluster.MapClusterItem
import com.project200.feature.matching.utils.FilterState
import com.project200.feature.matching.utils.MatchingFilterType
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorMain
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentBold
import com.project200.undabang.feature.matching.R

private val ToolbarShape = RoundedCornerShape(bottomStart = 15.dp, bottomEnd = 15.dp)

/**
 * 매칭 지도 화면
 *
 * KakaoMapView 로 지도를 호스팅하고, ViewModel 의 combinedMapData 를 구독해 마커/클러스터를 그린다.
 * 상단 툴바(장소 목록 버튼 + 필터 줄), 현재위치 버튼, 필터 로딩까지 한 Compose 트리에서 다룬다.
 *
 * @param onClusterClick 클러스터 라벨 클릭 시, 묶인 멤버 목록 전달
 * @param onPlaceMarkerClick 내 장소 마커 클릭 시
 * @param onCurrentLocationClick 현재위치 버튼 클릭 시 (권한/위치는 Fragment 처리)
 * @param onFilterClick 필터 칩 클릭 시 (바텀시트는 Fragment 처리)
 * @param onClearClick 필터 초기화 칩 클릭 시
 * @param onExercisePlaceListClick 운동 장소 목록 버튼 클릭 시
 */
@Composable
fun MatchingMapScreen(
    viewModel: MatchingMapViewModel,
    modifier: Modifier = Modifier,
    controller: MatchingMapController? = null,
    onMapReady: () -> Unit = {},
    onClusterClick: (List<MapClusterItem>) -> Unit = {},
    onPlaceMarkerClick: () -> Unit = {},
    onCurrentLocationClick: () -> Unit = {},
    onFilterClick: (MatchingFilterType) -> Unit = {},
    onClearClick: () -> Unit = {},
    onExercisePlaceListClick: () -> Unit = {},
) {
    val context = LocalContext.current
    // 멤버 데이터 구독
    val mapData by viewModel.combinedMapData.collectAsStateWithLifecycle()
    // 필터 상태/로딩 구독
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val isFilterLoading by viewModel.isFilterLoading.collectAsStateWithLifecycle()

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
                    ).also { mgr ->
                        controller?.manager = mgr // 카메라 제어 통로 연결
                        onMapReady() // Fragment가 초기 복원 등을 시작하도록 통지
                    }
            },
        )

        // 현재위치 버튼 오버레이 (권한/위치 처리는 콜백으로 Fragment에 위임)
        Image(
            painter = painterResource(R.drawable.ic_current_location),
            contentDescription = "현재 위치",
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(dimensionResource(com.project200.undabang.presentation.R.dimen.base_horizontal_margin))
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ColorWhite300)
                    .clickable { onCurrentLocationClick() }
                    .padding(10.dp),
        )

        // 상단 툴바: 운동 장소 목록 버튼 + 필터 칩 줄
        MatchingMapToolbar(
            filterState = filterState,
            onExercisePlaceListClick = onExercisePlaceListClick,
            onFilterClick = onFilterClick,
            onClearClick = onClearClick,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // 필터 로딩 오버레이 (터치 차단 + 진행 표시)
        if (isFilterLoading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x80000000))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = ColorMain)
            }
        }
    }

    // 데이터(또는 지도 준비) 변경 시 마커/클러스터 다시 그리기
    LaunchedEffect(mapData, manager) {
        val mgr = manager ?: return@LaunchedEffect
        val (members, places) = mapData
        updateClusterData(members, clusterCalculator)
        mgr.redrawMarkers(places, clusterCalculator)
    }
}

/** 지도 위 상단에 뜨는 툴바(흰 배경 + 하단 라운드). */
@Composable
private fun MatchingMapToolbar(
    filterState: FilterState,
    onExercisePlaceListClick: () -> Unit,
    onFilterClick: (MatchingFilterType) -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val horizontalMargin = dimensionResource(com.project200.undabang.presentation.R.dimen.base_horizontal_margin)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .shadow(2.dp, ToolbarShape)
                .background(ColorWhite300, ToolbarShape)
                .padding(top = 16.dp, bottom = horizontalMargin),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(start = horizontalMargin)
                    .clickable { onExercisePlaceListClick() }
                    .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.exercise_place_list),
                style = MaterialTheme.typography.contentBold,
                color = ColorBlack,
            )
            Icon(
                painter = painterResource(com.project200.undabang.presentation.R.drawable.ic_arrow_right),
                contentDescription = null,
                tint = ColorBlack,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        MatchingFilterRow(
            filterState = filterState,
            onFilterClick = onFilterClick,
            onClearClick = onClearClick,
            modifier = Modifier.fillMaxWidth(),
        )
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
