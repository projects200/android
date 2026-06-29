package com.project200.feature.matching.map.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import timber.log.Timber

/**
 * KakaoMap v2(MapView) 를 Compose 에서 사용하기 위한 재사용 Composable
 *
 * MapView 를 AndroidView 로 호스팅하고,
 * 생명주기(resume/pause/finish)를 DisposableEffect 로 연결한다.
 * MapView 단일 인스턴스 생성, start()/MapReadyCallback 캡슐화, 준비된 KakaoMap 을
 * [onMapReady] 로 노출한다.
 *
 * @param onMapReady 지도 준비 완료 시 KakaoMap 핸들 전달(여기서 MapViewManager 생성 등)
 * @param onMapError 지도 로딩 에러
 */
@Composable
fun KakaoMapView(
    modifier: Modifier = Modifier,
    onMapReady: (KakaoMap) -> Unit = {},
    onMapError: (Exception) -> Unit = {},
) {
    // Preview 에서는 네이티브 지도가 렌더되지 않으므로 빈 영역으로 early return
    if (LocalInspectionMode.current) {
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // recomposition 마다 재생성 금지
    val mapView = remember { MapView(context) }

    // 호스트 생명주기를 MapView 의 resume/pause/finish 로 전달
    DisposableEffect(lifecycleOwner, mapView) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> mapView.resume()
                    Lifecycle.Event.ON_PAUSE -> mapView.pause()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.finish() // 화면 이탈 시 정리(누수 방지)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.apply {
                start(
                    object : MapLifeCycleCallback() {
                        override fun onMapDestroy() {
                            Timber.d("KakaoMap destroyed")
                        }

                        override fun onMapError(error: Exception) {
                            Timber.e(error, "KakaoMap error")
                            onMapError(error)
                        }
                    },
                    object : KakaoMapReadyCallback() {
                        override fun onMapReady(map: KakaoMap) {
                            onMapReady(map)
                        }
                    },
                )
            }
        },
    )
}
