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
import com.kakao.vectormap.camera.CameraPosition
import com.kakao.vectormap.label.Label
import com.project200.common.constants.RuleConstants.SEOUL_CITY_HALL_LATITUDE
import com.project200.common.constants.RuleConstants.SEOUL_CITY_HALL_LONGITUDE
import com.project200.common.constants.RuleConstants.ZOOM_LEVEL
import com.project200.domain.model.MapPosition
import com.project200.domain.model.MatchingMember
import com.project200.feature.matching.map.cluster.ClusterCalculator
import com.project200.feature.matching.map.cluster.MapClusterItem
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.matching.R
import com.project200.undabang.feature.matching.databinding.FragmentMatchingMapBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MatchingMapFragment :
    BindingFragment<FragmentMatchingMapBinding>(R.layout.fragment_matching_map) {
    // MapViewManager로 지도 관련 로직 위임
    private var mapViewManager: MapViewManager? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val viewModel: MatchingMapViewModel by viewModels()

    private var isMapInitialized: Boolean = false

    // 클러스터링 계산을 위한 헬퍼 클래스
    private lateinit var clusterCalculator: ClusterCalculator<MapClusterItem>

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
        // 클러스터 계산기 초기화
        clusterCalculator = ClusterCalculator()
        isMapInitialized = false
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
                    // MapViewManager를 생성하여 지도 로직을 위임
                    mapViewManager =
                        MapViewManager(
                            context = requireContext(),
                            kakaoMap = map,
                            onCameraIdle = { cameraPosition -> handleCamera(cameraPosition) },
                            onLabelClick = { label -> handleLabelClick(label) },
                        )

                    setupMapRelatedObservers()
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

    /**
     * MapViewManager로부터 카메라 이동 완료 이벤트를 받아 처리합니다.
     */
    private fun handleCamera(cameraPosition: CameraPosition) {
        // 마지막 위치 저장
        viewModel.saveLastLocation(
            cameraPosition.position.latitude,
            cameraPosition.position.longitude,
            cameraPosition.zoomLevel,
        )

        // 카메라 위치가 바뀌면 클러스터도 변경됨 -> 마커 redraw
        mapViewManager?.redrawMarkers(myPlaces = viewModel.combinedMapData.value.second, clusterCalculator)
    }

    /**
     * MapViewManager로부터 라벨 클릭 이벤트를 받아 처리합니다.
     */
    private fun handleLabelClick(label: Label) {
        val cameraPosition = mapViewManager?.getCurrentCameraPosition() ?: return

        // 클릭한 라벨이 클러스터인지 확인
        clusterCalculator.getClusters(cameraPosition).find { cluster ->
            cluster.position.latitude == label.position.latitude &&
                cluster.position.longitude == label.position.longitude
        }?.let { foundCluster ->
            // 클러스터에 있는 운동 장소 리스트 표시
            showMembersBottomSheet(foundCluster.items.toList())
            return
        }

        // 클러스터를 찾지 못한 경우 == 내 장소 마커를 클릭한 경우
        findNavController().navigate(MatchingMapFragmentDirections.actionMatchingMapFragmentToExercisePlaceFragment())
    }

    override fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.shouldShowPlaceGuideDialog.collect {
                        showPlaceGuideDialog()
                    }
                }
                launch {
                    viewModel.shouldShowGuide.collect {
                        findNavController().navigate(
                            MatchingMapFragmentDirections.actionMatchingMapFragmentToMatchingGuideFragment(),
                        )
                    }
                }
                launch {
                    viewModel.errorEvents.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * 지도와 직접적으로 관련된 옵저버들을 설정합니다.
     */
    private fun setupMapRelatedObservers() {
        // 초기 지도 위치 관찰
        viewModel.initialMapPosition.observe(viewLifecycleOwner) { savedPosition ->
            // 지도의 초기 위치 설정이 아직 완료되지 않았을 때만 카메라를 이동시킵니다.
            if (isMapInitialized) return@observe

            if (isLocationPermissionGranted()) { // 권한이 있는 경우
                if (savedPosition != null) {
                    mapViewManager?.moveCamera(
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

                mapViewManager?.moveCamera(
                    LatLng.from(defaultPosition.latitude, defaultPosition.longitude),
                    defaultPosition.zoomLevel,
                )
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }

            isMapInitialized = true
        }

        // 회원 및 장소 데이터 통합 관찰
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.combinedMapData.collect { (members, places) ->
                    // 클러스터 계산기에 최신 회원 데이터 업데이트
                    updateClusterData(members)

                    // 데이터가 변경되었으므로 마커 redraw
                    mapViewManager?.redrawMarkers(places, clusterCalculator)
                }
            }
        }
    }

    /**
     * ViewModel의 최신 데이터를 ClusterCalculator에 업데이트합니다.
     */
    private fun updateClusterData(members: List<MatchingMember>) {
        val clusterItems = mutableListOf<MapClusterItem>()
        members.forEach { member ->
            member.locations.forEach { location ->
                clusterItems.add(MapClusterItem(member, location))
            }
        }

        clusterCalculator.clearItems()
        clusterCalculator.addItems(clusterItems)
    }

    private fun showPlaceGuideDialog() {
        val dialog =
            MatchingPlaceGuideDialog(
                onGoToPlaceRegister = {
                    findNavController().navigate(
                        MatchingMapFragmentDirections.actionMatchingMapFragmentToMatchingGuideFragment(),
                    )
                },
            )
        dialog.isCancelable = false // 바깥 영역 터치 시 다이얼로그가 닫히지 않도록 설정
        dialog.show(parentFragmentManager, this::class.java.simpleName)
    }

    private fun showMembersBottomSheet(items: List<MapClusterItem>) {
        val bottomSheet =
            MembersBottomSheetDialog(items) { item ->
                findNavController().navigate(
                    MatchingMapFragmentDirections.actionMatchingMapFragmentToMatchingProfileFragment(
                        memberId = item.member.memberId,
                        placeId = item.location.placeId),
                )
            }
        bottomSheet.show(parentFragmentManager, MembersBottomSheetDialog::class.java.simpleName)
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
            val latLng: LatLng
            if (location != null) {
                latLng = LatLng.from(location.latitude, location.longitude)
            } else {
                latLng = LatLng.from(SEOUL_CITY_HALL_LATITUDE, SEOUL_CITY_HALL_LONGITUDE)
                Toast.makeText(
                    requireContext(),
                    R.string.error_cannot_find_current_location,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            mapViewManager?.moveCamera(latLng, ZOOM_LEVEL)
        }
    }

    private fun isLocationPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onResume() {
        super.onResume()
        viewModel.fetchMatchingMembers()
        binding.mapView.resume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.pause()
    }
}
