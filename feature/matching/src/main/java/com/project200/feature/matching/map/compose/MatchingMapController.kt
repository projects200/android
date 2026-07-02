package com.project200.feature.matching.map.compose

import com.kakao.vectormap.LatLng
import com.project200.feature.matching.map.MapViewManager

/**
 * 지도 카메라를 화면 밖(Fragment)에서 제어하기 위한 통로.
 *
 * MapViewManager 는 MatchingMapScreen 내부에서 비동기로 생성되므로,
 * 권한/현재위치/초기 복원처럼 Fragment 가 담당하는 로직이 카메라를 만지려면 이 컨트롤러로 다리를 놓는다.
 * 준비 전(manager == null)에는 카메라 명령이 무시된다.
 */
class MatchingMapController {
    internal var manager: MapViewManager? = null

    fun moveCamera(
        latLng: LatLng,
        zoomLevel: Int,
    ) {
        manager?.moveCamera(latLng, zoomLevel)
    }
}
