package com.project200.feature.matching

import android.view.View
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.MapLifeCycleCallback
import com.project200.common.constants.RuleConstants.ZOOM_LEVEL
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.matching.R
import com.project200.undabang.feature.matching.databinding.FragmentMatchingMapBinding
import timber.log.Timber

class MatchingMapFragment : BindingFragment<FragmentMatchingMapBinding>(R.layout.fragment_matching_map) {
    private var kakaoMap: KakaoMap? = null

    override fun getViewBinding(view: View): FragmentMatchingMapBinding {
        return FragmentMatchingMapBinding.bind(view)
    }

    override fun setupViews() {
        initMapView()
    }

    private fun initMapView() {
        binding.mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {
                // 지도 API 가 정상적으로 종료될 때 호출
            }

            override fun onMapError(error: Exception) {
                // 인증 실패 및 지도 사용 중 에러 발생 시 호출
                Timber.d("$error")
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                kakaoMap = map
                // TODO: 마커 추가, 기본 카메라 위치 이동 등
            }

            override fun getZoomLevel(): Int {
                return ZOOM_LEVEL
            }
        })
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.resume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.pause()
    }


    override fun onDestroyView() {
        super.onDestroyView()
    }
}