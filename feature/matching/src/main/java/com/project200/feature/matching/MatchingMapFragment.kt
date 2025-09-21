package com.project200.feature.matching

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.project200.common.constants.RuleConstants.SEOUL_CITY_HALL_LATITUDE
import com.project200.common.constants.RuleConstants.SEOUL_CITY_HALL_LONGITUDE
import com.project200.common.constants.RuleConstants.ZOOM_LEVEL
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.matching.R
import com.project200.undabang.feature.matching.databinding.FragmentMatchingMapBinding
import timber.log.Timber

class MatchingMapFragment : BindingFragment<FragmentMatchingMapBinding>(R.layout.fragment_matching_map) {

    private var kakaoMap: KakaoMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // 위치 권한 요청을 위한 ActivityResultLauncher 정의
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // 권한이 허용되면 현재 위치로 이동
                moveToCurrentLocation()
            }
        }

    override fun getViewBinding(view: View): FragmentMatchingMapBinding {
        return FragmentMatchingMapBinding.bind(view)
    }

    override fun setupViews() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        initMapView()
        initListeners()
    }

    private fun initMapView() {
        binding.mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {
                // 지도 API 가 정상적으로 종료될 때 호출
            }

            override fun onMapError(error: Exception) {
                // 인증 실패 및 지도 사용 중 에러 발생 시 호출
                Timber.d("KakaoMap Error: $error")
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                kakaoMap = map
                setInitialMapPosition()
                setMapListeners()
            }

            override fun getZoomLevel(): Int {
                return ZOOM_LEVEL
            }
        })
    }

    private fun initListeners() {
        binding.currentLocationBtn.setOnClickListener {
            checkPermissionAndMove()
        }
    }

    private fun setMapListeners() {
        // 지도 이동이 끝나면 마지막 위치를 저장
        kakaoMap?.setOnCameraMoveEndListener { _, cameraPosition, _ ->
            saveLastLocation(
                cameraPosition.position.latitude,
                cameraPosition.position.longitude,
                cameraPosition.zoomLevel
            )
        }
    }

    /**
     * 지도의 초기 위치를 설정하는 함수
     */
    private fun setInitialMapPosition() {
        if (isLocationPermissionGranted()) {
            // [수정] SharedPreferences에서 직접 값을 불러와서 처리합니다.
            val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE)
            val lat = sharedPref?.getFloat(KEY_LAST_LAT, 0f) ?: 0f
            val lon = sharedPref?.getFloat(KEY_LAST_LON, 0f) ?: 0f
            val zoom = sharedPref?.getInt(KEY_LAST_ZOOM, 0) ?: 0

            if (lat != 0f && lon != 0f && zoom != 0) {
                // 저장된 값이 있으면 해당 위치와 줌 레벨로 카메라 이동
                moveCamera(LatLng.from(lat.toDouble(), lon.toDouble()), zoom)
            } else {
                // 저장된 값이 없으면 현재 위치로 이동
                moveToCurrentLocation()
            }
        } else {
            // 권한이 없다면 서울시청으로 이동하고 권한 요청
            moveCamera(LatLng.from(SEOUL_CITY_HALL_LATITUDE, SEOUL_CITY_HALL_LONGITUDE), ZOOM_LEVEL)
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /**
     * 버튼 클릭 시 권한 확인 후 현재 위치로 이동
     */
    private fun checkPermissionAndMove() {
        if (isLocationPermissionGranted()) {
            moveToCurrentLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /**
     * 현재 위치로 카메라를 이동시키는 함수
     */
    @SuppressLint("MissingPermission") // 권한 체크는 isLocationPermissionGranted()로 이미 수행됨
    private fun moveToCurrentLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                moveCamera(LatLng.from(location.latitude, location.longitude), ZOOM_LEVEL)
            } else {
                moveCamera(LatLng.from(SEOUL_CITY_HALL_LATITUDE, SEOUL_CITY_HALL_LONGITUDE), ZOOM_LEVEL)
            }
        }
    }

    private fun moveCamera(latLng: LatLng, zoomLevel: Int) {
        kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(latLng, zoomLevel))
    }

    private fun isLocationPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // 마지막 위치 저장을 위한 SharedPreferences 로직
    private fun saveLastLocation(latitude: Double, longitude: Double, zoomLevel: Int) {
        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return
        with(sharedPref.edit()) {
            putFloat(KEY_LAST_LAT, latitude.toFloat())
            putFloat(KEY_LAST_LON, longitude.toFloat())
            putInt(KEY_LAST_ZOOM, zoomLevel)
            apply()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.resume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.pause()
    }

    companion object {
        private const val KEY_LAST_LAT = "key_last_lat"
        private const val KEY_LAST_LON = "key_last_lon"
        private const val KEY_LAST_ZOOM = "key_last_zoom"
    }
}