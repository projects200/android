package com.project200.feature.matching.map

import android.content.Context
import androidx.core.content.ContextCompat
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.camera.CameraPosition
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.label.LabelTextBuilder
import com.kakao.vectormap.label.LabelTextStyle
import com.project200.domain.model.ExercisePlace
import com.project200.domain.model.MapBounds
import com.project200.feature.matching.map.cluster.ClusterCalculator
import com.project200.feature.matching.map.cluster.ClusterMarkerHelper.createClusterBitmap
import com.project200.feature.matching.map.cluster.ClusterMarkerHelper.getClusterSizePx
import com.project200.feature.matching.map.cluster.MapClusterItem
import com.project200.undabang.feature.matching.R
import ted.gun0912.clustering.clustering.Cluster

/**
 * KakaoMap과 관련된 UI 로직을 전담하는 클래스.
 *
 * @param context 리소스 접근을 위한 Context.
 * @param kakaoMap 관리할 KakaoMap 인스턴스.
 * @param onCameraIdle 지도의 카메라 이동이 멈췄을 때 호출될 콜백.
 * @param onLabelClick 지도 위의 라벨(마커)이 클릭됐을 때 호출될 콜백.
 */
class MapViewManager(
    private val context: Context,
    private val kakaoMap: KakaoMap,
    private val onCameraIdle: (cameraPosition: CameraPosition) -> Unit,
    private val onLabelClick: (label: Label) -> Unit,
) {
    private val currentLabels = mutableListOf<Label>()
    private val labelManager = kakaoMap.labelManager ?: throw IllegalStateException("LabelManager is null")

    // 텍스트 스타일은 한 번만 생성하여 재사용
    private val textStyle =
        LabelTextStyle.from(
            context.resources.getDimensionPixelSize(R.dimen.cluster_text_size),
            ContextCompat.getColor(context, com.project200.undabang.presentation.R.color.white300),
        ).apply {
            setFont(com.project200.undabang.presentation.R.font.pretendard_bold)
        }

    init {
        setupMap()
    }

    /**
     * 지도 초기 설정(제스처, 리스너)을 수행합니다.
     */
    private fun setupMap() {
        // 제스처 비활성화
        kakaoMap.setGestureEnable(com.kakao.vectormap.GestureType.Rotate, false)
        kakaoMap.setGestureEnable(com.kakao.vectormap.GestureType.Tilt, false)

        // 리스너 설정
        kakaoMap.setOnCameraMoveEndListener { _, cameraPosition, _ ->
            onCameraIdle(cameraPosition)
        }

        kakaoMap.setOnLabelClickListener { _, _, label ->
            onLabelClick(label)
            // 이벤트 소비를 위해 true 반환
            true
        }
    }

    /**
     * 지정된 위치와 줌 레벨로 카메라를 이동시킵니다.
     */
    fun moveCamera(
        latLng: LatLng,
        zoomLevel: Int,
    ) {
        kakaoMap.moveCamera(CameraUpdateFactory.newCenterPosition(latLng, zoomLevel))
    }

    /**
     * 지도 위의 모든 마커를 새로 그립니다.
     * 이 메서드는 내부적으로 현재 카메라 위치를 가져와 클러스터를 계산합니다.
     * @param myPlaces 지도에 표시할 '내 장소' 목록
     * @param clusterCalculator 클러스터링을 수행할 계산기 인스턴스
     */
    fun redrawMarkers(
        myPlaces: List<ExercisePlace>,
        clusterCalculator: ClusterCalculator<MapClusterItem>,
    ) {
        // Manager가 내부적으로 관리하는 kakaoMap 객체에서 직접 cameraPosition을 가져옵니다.
        val currentCameraPosition = kakaoMap.cameraPosition ?: return

        // 현재 카메라 위치를 기반으로 클러스터를 계산합니다.
        val clusters = clusterCalculator.getClusters(currentCameraPosition)

        // 기존 라벨 모두 제거
        currentLabels.forEach { it.remove() }
        currentLabels.clear()

        // 내 장소 마커 추가
        myPlaces.forEach { place ->
            val options =
                LabelOptions.from(LatLng.from(place.latitude, place.longitude))
                    .setStyles(R.drawable.ic_place_marker)
                    .setTag(place)
            labelManager.layer?.addLabel(options)
        }

        // 클러스터 마커 추가
        clusters.forEach { cluster ->
            addClusterLabel(cluster)
        }
    }

    fun getCurrentCameraPosition(): CameraPosition? {
        return kakaoMap.cameraPosition
    }

    private fun addClusterLabel(cluster: Cluster<MapClusterItem>) {
        val position = LatLng.from(cluster.position.latitude, cluster.position.longitude)

        val backgroundBitmap =
            createClusterBitmap(
                sizePx = getClusterSizePx(context, cluster.size),
                markerColor = ContextCompat.getColor(context, R.color.marker_color),
                strokeColor = ContextCompat.getColor(context, R.color.marker_stroke_color),
            )

        // 배경 아이콘 Label
        val backgroundOptions =
            LabelOptions.from(position)
                .setStyles(LabelStyle.from(backgroundBitmap).setAnchorPoint(0.5f, 0.5f))
                .setRank(0)
                .setTag(cluster)
        labelManager.layer?.addLabel(backgroundOptions)?.let { currentLabels.add(it) }

        // 텍스트 아이콘 Label
        val textOptions =
            LabelOptions.from(position)
                .setStyles(LabelStyles.from(LabelStyle.from(textStyle)))
                .setTexts(LabelTextBuilder().addTextLine(cluster.size.toString(), 0))
                .setRank(1)
                .setTag(cluster)
        labelManager.layer?.addLabel(textOptions)?.let { currentLabels.add(it) }
    }

    /**
     * 현재 지도의 경계 좌표를 반환합니다.
     */
    fun getCurrentBounds(): MapBounds? {
        val width = kakaoMap.viewport.width()
        val height = kakaoMap.viewport.height()

        // 화면의 좌상단(0,0)과 우하단(width, height) 픽셀 좌표를 위경도로 변환
        val topLeft = kakaoMap.fromScreenPoint(0, 0)
        val bottomRight = kakaoMap.fromScreenPoint(width, height)

        if (topLeft == null || bottomRight == null) return null

        return MapBounds(
            topLeftLat = topLeft.latitude,
            topLeftLng = topLeft.longitude,
            bottomRightLat = bottomRight.latitude,
            bottomRightLng = bottomRight.longitude,
        )
    }
}
