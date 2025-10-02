package com.project200.feature.matching.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
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
import com.project200.domain.model.ExercisePlace
import com.project200.domain.model.MapPosition
import com.project200.domain.model.MatchingMember
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.matching.R
import com.project200.undabang.feature.matching.databinding.FragmentMatchingMapBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MatchingMapFragment :
    BindingFragment<FragmentMatchingMapBinding>(R.layout.fragment_matching_map) {
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

                    // 두 손가락으로 지도를 회전시키는 제스처 비활성화
                    kakaoMap?.setGestureEnable(com.kakao.vectormap.GestureType.Rotate, false)
                    // 두 손가락으로 지도를 기울이는 제스처(틸트) 비활성화
                    kakaoMap?.setGestureEnable(com.kakao.vectormap.GestureType.Tilt, false)

                    viewModel.fetchMatchingMembers()
                }
            },
        )
    }

    private fun initListeners() {
        binding.currentLocationBtn.setOnClickListener {
            checkPermissionAndMove()
        }

        binding.exercisePlaceListBtn.setOnClickListener {
            findNavController().navigate(
                MatchingMapFragmentDirections.actionMatchingMapFragmentToExercisePlaceFragment(),
            )
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

        kakaoMap?.setOnLabelClickListener { _, _, label ->
            // label.tag의 타입에 따라 다른 동작을 수행
            when (val clickedTag = label.tag) {
                is MatchingMember -> {
                    findNavController().navigate(
                        MatchingMapFragmentDirections.actionMatchingMapFragmentToMatchingProfileFragment(
                            memberId = clickedTag.memberId,
                        ),
                    )
                }

                is ExercisePlace -> {
                    findNavController().navigate(
                        MatchingMapFragmentDirections.actionMatchingMapFragmentToExercisePlaceFragment(),
                    )
                }
            }
            true
        }
    }

    override fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.shouldNavigateToGuide.collect {
                        findNavController().navigate(
                            MatchingMapFragmentDirections.actionMatchingMapFragmentToMatchingGuideFragment(),
                        )
                    }
                }
                launch {
                    viewModel.shouldShowPlaceDialog.collect {
                        showPlaceGuideDialog()
                    }
                }
                // --- 추가: 에러 이벤트 구독 ---
                launch {
                    viewModel.errorEvents.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
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

        // --- 변경: 개별 LiveData 구독 대신, 결합된 StateFlow를 구독 ---
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // member와 place 데이터가 합쳐진 최종 UI 상태를 구독
                viewModel.combinedMapData.collect { (members, places) ->
                    // Pair를 구조 분해하여 사용
                    redrawAllMarkers(members, places)
                }
            }
        }
    }

    /**
     * 지도 위의 모든 마커를 지우고, 전달받은 최신 데이터로 다시 그립니다.
     * @param members 지도에 표시할 회원 목록
     * @param places 지도에 표시할 운동 장소 목록
     */
    private fun redrawAllMarkers(
        members: List<MatchingMember>,
        places: List<ExercisePlace>,
    ) {
        val labelManager = kakaoMap?.labelManager ?: return
        labelManager.clearAll()

        // 운동 장소 마커를 그립니다.
        places.forEach { place ->
            val options =
                LabelOptions.from(LatLng.from(place.latitude, place.longitude))
                    .setStyles(R.drawable.ic_place_marker)
                    .setTag(place) // 태그에 place 객체 저장
            labelManager.layer?.addLabel(options)
        }

        // 회원 마커를 그립니다.
        members.forEach { member ->
            member.locations.forEach { location ->
                val options =
                    LabelOptions.from(LatLng.from(location.latitude, location.longitude))
                        .setStyles(R.drawable.ic_member_marker_png)
                        .setTag(member) // 태그에 member 객체 저장
                labelManager.layer?.addLabel(options)
            }
        }
    }

    private fun showPlaceGuideDialog() {
        val dialog =
            MatchingPlaceGuideDialog(
                onGoToPlaceRegister = {
                    findNavController().navigate(
                        MatchingMapFragmentDirections.actionMatchingMapFragmentToExercisePlaceSearchFragment(),
                    )
                },
            )
        dialog.isCancelable = false // 바깥 영역 터치 시 다이얼로그가 닫히지 않도록 설정
        dialog.show(parentFragmentManager, this::class.java.simpleName)
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
                moveCamera(
                    LatLng.from(SEOUL_CITY_HALL_LATITUDE, SEOUL_CITY_HALL_LONGITUDE),
                    ZOOM_LEVEL,
                )
                Toast.makeText(
                    requireContext(),
                    R.string.error_cannot_find_current_location,
                    Toast.LENGTH_SHORT,
                ).show()
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
        viewModel.checkHasPlaceGuideBeenShown()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.pause()
    }
}
