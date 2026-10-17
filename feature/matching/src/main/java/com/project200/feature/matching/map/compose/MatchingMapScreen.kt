package com.project200.feature.matching.map.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kakao.vectormap.label.Label
import com.project200.domain.model.ExercisePlace
import com.project200.domain.model.MatchingMember
import com.project200.feature.matching.MatchingFixtureEvidence
import com.project200.feature.matching.map.MapViewManager
import com.project200.feature.matching.map.MatchingMapLabelKind
import com.project200.feature.matching.map.MatchingMapReadiness
import com.project200.feature.matching.map.MatchingMapStatus
import com.project200.feature.matching.map.MatchingMapTarget
import com.project200.feature.matching.map.MatchingMapViewModel
import com.project200.feature.matching.map.cluster.ClusterCalculator
import com.project200.feature.matching.map.cluster.MapClusterItem
import com.project200.feature.matching.utils.FilterState
import com.project200.feature.matching.utils.MatchingFilterType
import com.project200.presentation.compose.map.KakaoMapView
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorMain
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentBold
import com.project200.undabang.feature.matching.R
import ted.gun0912.clustering.clustering.Cluster

private val ToolbarShape = RoundedCornerShape(bottomStart = 15.dp, bottomEnd = 15.dp)

/**
 * 매칭 지도 화면 (stateful).
 *
 * ViewModel 의 상태를 구독하고 지도 호스팅(manager 생성·카메라 idle·라벨 클릭)을 담당한 뒤,
 * 화면 레이아웃/오버레이는 stateless 인 [MatchingMapContent] 에 위임한다. 지도는 슬롯으로 넘긴다.
 *
 * @param onClusterClick 클러스터 라벨 클릭 시, 묶인 멤버 목록 전달
 * @param onPlaceMarkerClick 내 장소 마커 클릭 시
 * @param onCurrentLocationClick 현재위치 버튼 클릭 시 (권한/위치는 Fragment 처리)
 * @param onFilterClick 필터 칩 클릭 시 (바텀시트는 Fragment 처리)
 * @param onClearClick 필터 초기화 칩 클릭 시
 * @param onExercisePlaceListClick 운동 장소 목록 버튼 클릭 시
 */
@OptIn(ExperimentalComposeUiApi::class)
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
    // 멤버 데이터 / 필터 상태 / 로딩 구독
    val mapData by viewModel.combinedMapData.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val isFilterLoading by viewModel.isFilterLoading.collectAsStateWithLifecycle()

    // 클러스터 계산기 / 지도 매니저(지도 준비 후 비동기 생성이라 상태로 둠)
    val clusterCalculator = remember { ClusterCalculator<MapClusterItem>() }
    var manager by remember(context) { mutableStateOf<MapViewManager?>(null) }
    var mapReadiness by remember(context) { mutableStateOf(MatchingMapReadiness()) }
    var mapAttempt by remember(context) { mutableStateOf<Int?>(null) }
    val markerTargets =
        remember(context) {
            if (MatchingFixtureEvidence.isArmed(context)) mutableStateOf(emptyList<MatchingMapTarget>()) else null
        }
    val mapSize = remember(context) { if (markerTargets != null) mutableStateOf(IntSize.Zero) else null }
    if (markerTargets != null) {
        DisposableEffect(context) {
            onDispose { manager?.clearMarkerTargets() }
        }
    }

    MatchingMapContent(
        filterState = filterState,
        isFilterLoading = isFilterLoading,
        onCurrentLocationClick = onCurrentLocationClick,
        onFilterClick = onFilterClick,
        onClearClick = onClearClick,
        onExercisePlaceListClick = onExercisePlaceListClick,
        modifier =
            if (markerTargets != null) {
                modifier
                    .semantics {
                        testTagsAsResourceId = true
                    }
                    .testTag(mapReadiness.testTag)
            } else {
                modifier
            },
    ) {
        // 지도 슬롯: 호스팅 + manager 생성 + 카메라 idle/라벨 클릭 처리
        KakaoMapView(
            modifier =
                if (mapSize != null) {
                    Modifier.fillMaxSize().onSizeChanged { size ->
                        mapSize.value = size
                        manager?.updateMarkerTargetLayout(size)
                    }
                } else {
                    Modifier.fillMaxSize()
                },
            onMapStarted = {
                manager?.clearMarkerTargets()
                manager = null
                controller?.manager = null
                mapReadiness = MatchingMapReadiness()
                markerTargets?.value = emptyList()
                mapAttempt = MatchingFixtureEvidence.mapStarted(context)
            },
            onMapError = { error ->
                if (mapReadiness.status != MatchingMapStatus.FAILED) {
                    mapReadiness = mapReadiness.failed(error)
                    manager?.clearMarkerTargets()
                    manager = null
                    controller?.manager = null
                    markerTargets?.value = emptyList()
                    MatchingFixtureEvidence.map(context, mapAttempt, mapReadiness)
                }
            },
            onMapReady = { map ->
                if (mapReadiness.status == MatchingMapStatus.FAILED) return@KakaoMapView
                val attempt = mapAttempt
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
                            val kind = handleLabelClick(label, onClusterClick, onPlaceMarkerClick)
                            MatchingFixtureEvidence.mapLabelClick(context, mapAttempt, kind)
                        },
                        onMarkerTargetsChanged =
                            if (markerTargets != null) {
                                { targets ->
                                    if (mapAttempt == attempt && mapReadiness.status != MatchingMapStatus.FAILED) {
                                        markerTargets.value = targets
                                    }
                                }
                            } else {
                                null
                            },
                    ).also { mgr ->
                        mapSize?.let { mgr.updateMarkerTargetLayout(it.value) }
                        controller?.manager = mgr // 카메라 제어 통로 연결
                        onMapReady() // Fragment가 초기 복원 등을 시작하도록 통지
                    }
                mapReadiness = mapReadiness.ready()
                MatchingFixtureEvidence.map(context, mapAttempt, mapReadiness)
            },
        )
        if (
            markerTargets != null && mapReadiness.status == MatchingMapStatus.READY &&
            MatchingFixtureEvidence.isArmed(context)
        ) {
            MatchingMapMarkerTargets(markerTargets.value)
        }
        if (mapReadiness.status == MatchingMapStatus.FAILED) {
            Text(
                text = stringResource(R.string.error_unknown),
                modifier = Modifier.align(Alignment.Center).background(ColorWhite300).padding(16.dp),
                color = ColorBlack,
            )
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

/** 접근성 경계만 제공합니다. 클릭 동작·포인터 처리는 없어 실제 터치는 아래 MapView로 전달됩니다. */
@Composable
private fun MatchingMapMarkerTargets(targets: List<MatchingMapTarget>) {
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            targets.forEach { target ->
                Box(
                    Modifier.semantics {
                        contentDescription =
                            target.members.joinToString(",") {
                                "matching_marker_${it.member.memberId}_${it.location.placeId}"
                            }
                    },
                )
            }
        },
    ) { measurables, constraints ->
        val placeables =
            measurables.mapIndexed { index, measurable ->
                val bounds = targets[index].bounds
                measurable.measure(Constraints.fixed(bounds.width, bounds.height))
            }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val bounds = targets[index].bounds
                placeable.place(bounds.left, bounds.top)
            }
        }
    }
}

/**
 * 매칭 지도 화면의 stateless 레이아웃.
 *
 * 지도 본체는 [mapContent] 슬롯으로 받고, 오버레이(현재위치 버튼·상단 툴바·필터 로딩)는
 * 순수 상태([filterState], [isFilterLoading])와 콜백으로 그린다. ViewModel 의존이 없어 Preview 가능.
 */
@Composable
fun MatchingMapContent(
    filterState: FilterState,
    isFilterLoading: Boolean,
    onCurrentLocationClick: () -> Unit,
    onFilterClick: (MatchingFilterType) -> Unit,
    onClearClick: () -> Unit,
    onExercisePlaceListClick: () -> Unit,
    modifier: Modifier = Modifier,
    mapContent: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize()) {
        mapContent()

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

/** SDK가 실제로 반환한 라벨의 태그를 사용합니다. 새 카메라의 클러스터 좌표로 종류를 추정하지 않습니다. */
internal fun handleLabelClick(
    label: Label,
    onClusterClick: (List<MapClusterItem>) -> Unit,
    onPlaceMarkerClick: () -> Unit,
): MatchingMapLabelKind {
    return when (val target = label.tag) {
        is Cluster<*> -> {
            val items = target.items.map { it as? MapClusterItem ?: return MatchingMapLabelKind.UNKNOWN }
            onClusterClick(items)
            MatchingMapLabelKind.CLUSTER
        }
        is ExercisePlace -> {
            onPlaceMarkerClick()
            MatchingMapLabelKind.PLACE
        }
        else -> MatchingMapLabelKind.UNKNOWN
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun MatchingMapContentPreview() {
    AppTheme {
        MatchingMapContent(
            filterState = FilterState(),
            isFilterLoading = false,
            onCurrentLocationClick = {},
            onFilterClick = {},
            onClearClick = {},
            onExercisePlaceListClick = {},
        ) {
            // 지도 자리 placeholder (실제 지도는 네이티브라 Preview 불가)
            Box(Modifier.fillMaxSize().background(ColorGray200))
        }
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun MatchingMapContentLoadingPreview() {
    AppTheme {
        MatchingMapContent(
            filterState = FilterState(),
            isFilterLoading = true,
            onCurrentLocationClick = {},
            onFilterClick = {},
            onClearClick = {},
            onExercisePlaceListClick = {},
        ) {
            Box(Modifier.fillMaxSize().background(ColorGray200))
        }
    }
}
