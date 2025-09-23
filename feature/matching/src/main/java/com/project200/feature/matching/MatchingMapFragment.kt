package com.project200.feature.matching

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
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
import com.kakao.vectormap.label.LabelOptions
import com.project200.common.constants.RuleConstants.SEOUL_CITY_HALL_LATITUDE
import com.project200.common.constants.RuleConstants.SEOUL_CITY_HALL_LONGITUDE
import com.project200.common.constants.RuleConstants.ZOOM_LEVEL
import com.project200.domain.model.BaseResult
import com.project200.domain.model.MapPosition
import com.project200.domain.model.MatchingMember
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.matching.R
import com.project200.undabang.feature.matching.databinding.FragmentMatchingMapBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MatchingMapFragment : BindingFragment<FragmentMatchingMapBinding>(R.layout.fragment_matching_map) {
    private var kakaoMap: KakaoMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val viewModel: MatchingMapViewModel by viewModels()

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
        binding.mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() {}

                override fun onMapError(error: Exception) {
                    Timber.d("$error")
                }
            },
            object : KakaoMapReadyCallback() {
                override fun onMapReady(map: KakaoMap) {
                    kakaoMap = map

                    setMapListeners()
                    setupMapRelatedObservers()

                    viewModel.fetchMatchingMembers()
                }
            },
        )
    }

    private fun initListeners() {
        binding.currentLocationBtn.setOnClickListener {
            checkPermissionAndMove()
        }
    }

    private fun setMapListeners() {
        // 지도 이동이 끝나면 마지막 위치를 저장
        kakaoMap?.setOnCameraMoveEndListener { _, cameraPosition, _ ->
            viewModel.saveLastLocation(
                cameraPosition.position.latitude,
                cameraPosition.position.longitude,
                cameraPosition.zoomLevel,
            )
        }
    }

    // 지도와 직접적으로 관련된 옵저버
    private fun setupMapRelatedObservers() {
        // 초기 지도 위치 관찰
        viewModel.initialMapPosition.observe(viewLifecycleOwner) { savedPosition ->
            if (isLocationPermissionGranted()) { // 권한이 있는 경우
                if (savedPosition != null) {
                    moveCamera(
                        LatLng.from(savedPosition.latitude, savedPosition.longitude),
                        savedPosition.zoomLevel,
                    )
                } else {
                    // 저장된 위치가 없으면, 현재 위치로 이동
                    moveToCurrentLocation()
                }
            } else {
                // 위치 권한이 없는 경우
                // 기본위치(서울시청)로 이동
                val defaultPosition =
                    MapPosition(
                        latitude = SEOUL_CITY_HALL_LATITUDE,
                        longitude = SEOUL_CITY_HALL_LONGITUDE,
                        zoomLevel = ZOOM_LEVEL,
                    )
                moveCamera(
                    LatLng.from(defaultPosition.latitude, defaultPosition.longitude),
                    defaultPosition.zoomLevel,
                )
                // 사용자에게 권한을 요청
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        // 매칭 회원 목록 관찰
        viewModel.matchingMembers.observe(viewLifecycleOwner) { result ->
            when (result) {
                is BaseResult.Success -> {
                    // 성공 시 마커 표시
                    drawMemberMarkers(result.data)
                }
                is BaseResult.Error -> {
                    // 실패 시 Toast 메시지 표시
                    val errorMessage = result.message ?: getString(R.string.error_unknown)
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 전달받은 회원 목록을 지도에 마커로 표시합니다.
     */
    private fun drawMemberMarkers(members: List<MatchingMember>) {
        val labelManager = kakaoMap?.labelManager ?: return
        labelManager.clearAll() // labelManager를 통해 안전하게 초기화

        members.forEach { member ->
            member.locations.forEach { location ->
                val options =
                    LabelOptions.from(LatLng.from(location.latitude, location.longitude))
                        .setStyles(R.drawable.ic_member_marker_png)
                        .setTag(member)

                labelManager.layer?.addLabel(options)
            }
        }

        kakaoMap?.setOnLabelClickListener { kakaoMap, labelLayer, label ->
            val clickedMember = label.tag as? MatchingMember
            if (clickedMember != null) {
                Toast.makeText(requireContext(), clickedMember.nickname, Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

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
                // 현재 위치를 가져오지 못한 경우 기본 위치로 이동
                moveCamera(LatLng.from(SEOUL_CITY_HALL_LATITUDE, SEOUL_CITY_HALL_LONGITUDE), ZOOM_LEVEL)
                Toast.makeText(requireContext(), R.string.error_cannot_find_current_location, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun moveCamera(
        latLng: LatLng,
        zoomLevel: Int,
    ) {
        kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(latLng, zoomLevel))
    }

    private fun isLocationPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION,
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
