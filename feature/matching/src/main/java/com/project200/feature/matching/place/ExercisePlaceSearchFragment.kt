package com.project200.feature.matching.place

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.project200.common.constants.RuleConstants
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.matching.R
import com.project200.undabang.feature.matching.databinding.FragmentExercisePlaceSearchBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class ExercisePlaceSearchFragment : BindingFragment<FragmentExercisePlaceSearchBinding>(R.layout.fragment_exercise_place_search) {

    private var kakaoMap: KakaoMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val viewModel: ExercisePlaceSearchViewModel by viewModels()

    // 위치 권한 요청 런처
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // 권한이 허용되면 현재 위치로 이동
                moveToCurrentLocation()
            }
        }

    override fun getViewBinding(view: View): FragmentExercisePlaceSearchBinding {
        return FragmentExercisePlaceSearchBinding.bind(view)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        initMapView()
    }

    override fun setupViews() {
        // TODO: 툴바 설정 등 필요한 View 초기화
    }

    // 지도 초기화 함수
    private fun initMapView() {
        binding.mapView.start( // XML에 MapView가 있고 ID가 mapView라고 가정
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() {}
                override fun onMapError(error: Exception) {
                    Timber.d("$error")
                }
            },
            object : KakaoMapReadyCallback() {
                override fun onMapReady(map: KakaoMap) {
                    kakaoMap = map
                    setupInitialMapPosition()
                }
            }
        )
    }


    /**
     * 초기 지도 위치 설정 함수
     * 위치 권한이 있으면 현재 위치로, 없으면 서울시청으로 이동
     */
    private fun setupInitialMapPosition() {
        if (isLocationPermissionGranted()) {
            // 권한이 있으면 현재 위치로 바로 이동
            moveToCurrentLocation()
        } else {
            // 권한이 없으면 기본 위치(서울시청)로 이동하고 권한 요청
            moveCamera(
                LatLng.from(RuleConstants.SEOUL_CITY_HALL_LATITUDE, RuleConstants.SEOUL_CITY_HALL_LONGITUDE),
            )
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /**
     * 현재 위치로 카메라를 이동시키는 함수
     */
    @SuppressLint("MissingPermission")
    private fun moveToCurrentLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                moveCamera(LatLng.from(location.latitude, location.longitude),)
            } else {
                // 현재 위치를 가져오지 못한 경우 기본 위치로 이동
                moveCamera(LatLng.from(RuleConstants.SEOUL_CITY_HALL_LATITUDE, RuleConstants.SEOUL_CITY_HALL_LONGITUDE))
                Toast.makeText(requireContext(), R.string.error_cannot_find_current_location, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 카메라를 지정된 위치로 이동시키는 함수
     */
    private fun moveCamera(latLng: LatLng) {
        kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(latLng))
    }

    private fun isLocationPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.resume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.pause()
    }
}