package com.project200.feature.matching.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getColor
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
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.label.LabelTextBuilder
import com.kakao.vectormap.label.LabelTextStyle
import com.project200.common.constants.RuleConstants.SEOUL_CITY_HALL_LATITUDE
import com.project200.common.constants.RuleConstants.SEOUL_CITY_HALL_LONGITUDE
import com.project200.common.constants.RuleConstants.ZOOM_LEVEL
import com.project200.domain.model.ExercisePlace
import com.project200.domain.model.MapPosition
import com.project200.domain.model.MatchingMember
import com.project200.feature.matching.map.cluster.ClusterCalculator
import com.project200.feature.matching.map.cluster.ClusterMarkerHelper.createClusterBitmap
import com.project200.feature.matching.map.cluster.ClusterMarkerHelper.getClusterSizePx
import com.project200.feature.matching.map.cluster.MapClusterItem
import com.project200.feature.matching.map.cluster.MemberClusterItem
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.matching.R
import com.project200.undabang.feature.matching.databinding.FragmentMatchingMapBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ted.gun0912.clustering.clustering.Cluster
import timber.log.Timber

@AndroidEntryPoint
class MatchingMapFragment :
    BindingFragment<FragmentMatchingMapBinding>(R.layout.fragment_matching_map) {
    private var kakaoMap: KakaoMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val viewModel: MatchingMapViewModel by viewModels()

    private var isMapInitialized: Boolean = false

    // 클러스터링 계산을 위한 헬퍼 클래스
    private lateinit var clusterCalculator: ClusterCalculator<MapClusterItem>
    // 현재 지도에 그려진 라벨(마커)들을 추적하기 위한 리스트
    private val currentLabels = mutableListOf<Label>()

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
                    kakaoMap = map

                    // 지도가 준비된 후 리스너 설정 및 데이터 관찰 시작
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

    /**
     * 지도 이동 및 라벨 클릭 리스너를 설정합니다.
     */
    private fun setMapListeners() {
        // 지도 이동이 끝나면 클러스터를 다시 계산하고 마커를 새로 그립니다.
        kakaoMap?.setOnCameraMoveEndListener { _, cameraPosition, _ ->
            viewModel.saveLastLocation(
                cameraPosition.position.latitude,
                cameraPosition.position.longitude,
                cameraPosition.zoomLevel,
            )

            // 현재 지도에 표시할 내 장소 데이터와 회원 데이터를 가져옵니다.
            val currentMyPlaces = viewModel.combinedMapData.value.second
            val currentMemberPlaces = viewModel.combinedMapData.value.first

            // 데이터가 로드되지 않은 초기 상태에서 불필요한 재계산을 방지합니다.
            if (currentMemberPlaces.isEmpty() && currentMyPlaces.isEmpty()) {
                return@setOnCameraMoveEndListener
            }

            // 두 데이터를 모두 사용하여 마커를 다시 그립니다.
            redrawAllMarkers(currentMyPlaces, clusterCalculator.getClusters(cameraPosition))
        }

        // 라벨(마커) 클릭 리스너
        kakaoMap?.setOnLabelClickListener { _, _, label ->
            when (label.tag) {
                is Cluster<*> -> { // 클러스터 마커 클릭 시
                    // TODO: 클러스터 아이템에 포함되어 있는 장소 리스트 표시
                }
                is ExercisePlace -> { // 내 장소 아이템 클릭 시
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
                    viewModel.shouldShowPlaceDialog.collect {
                        showPlaceGuideDialog()
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

            isMapInitialized = true
        }

        // 회원 및 장소 데이터 통합 관찰
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.combinedMapData.collect { (members, places) ->
                    // 원본 데이터를 클러스터 계산기에 업데이트
                    updateClusterData(members)

                    val clusters = kakaoMap?.cameraPosition?.let {
                        clusterCalculator.getClusters(it)
                    } ?: emptySet()

                    // 지도에 마커를 새로 그리기
                    redrawAllMarkers(places, clusters)
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
                clusterItems.add(MemberClusterItem(member, location))
            }
        }

        clusterCalculator.clearItems()
        clusterCalculator.addItems(clusterItems)
    }

    /**
     * 계산된 클러스터 정보를 바탕으로 지도 위의 마커를 새로 그립니다.
     */
    private fun redrawAllMarkers(myPlaces: List<ExercisePlace>, clusters: Set<Cluster<MapClusterItem>>) {
        val labelManager = kakaoMap?.labelManager ?: return

        currentLabels.forEach { it.remove() }
        currentLabels.clear()

        myPlaces.forEach { place ->
            val options =
                LabelOptions.from(LatLng.from(place.latitude, place.longitude))
                    .setStyles(R.drawable.ic_place_marker)
                    .setTag(place)
            labelManager.layer?.addLabel(options)
        }

        // 텍스트 스타일을 한 번만 생성
        val textStyle = LabelTextStyle.from(
            resources.getDimensionPixelSize(R.dimen.cluster_text_size),
            getColor(requireContext(), com.project200.undabang.presentation.R.color.white300)
        ).apply {
            setFont(com.project200.undabang.presentation.R.font.pretendard_bold)
        }

        clusters.forEach { cluster ->
            addClusterLabel(cluster, textStyle)
        }
    }

    private fun addClusterLabel(cluster: Cluster<MapClusterItem>, textStyle: LabelTextStyle) {
        val labelManager = kakaoMap?.labelManager ?: return
        val position = LatLng.from(cluster.position.latitude, cluster.position.longitude)
        // 아이템 개수에 따라 동적으로 마커를 생성
        // 원형 비트맵 생성
        val backgroundBitmap = createClusterBitmap(
            sizePx = getClusterSizePx(requireContext(), cluster.size),
            markerColor = getColor(requireContext(), R.color.marker_color),
            strokeColor = getColor(requireContext(), R.color.marker_stroke_color)
        )

        // 배경 아이콘 Label 생성
        val backgroundOptions = LabelOptions.from(position)
            .setStyles(LabelStyle.from(backgroundBitmap).setAnchorPoint(0.5f, 0.5f))
            .setRank(0)
            .setTag(cluster)
        labelManager.layer?.addLabel(backgroundOptions)?.let { currentLabels.add(it) }

        // 텍스트 아이콘 Label 생성
        val textOptions = LabelOptions.from(position)
            .setStyles(LabelStyles.from(LabelStyle.from(textStyle)))
            .setTexts(LabelTextBuilder().addTextLine(cluster.size.toString(), 0))
            .setRank(1)
            .setTag(cluster)
        labelManager.layer?.addLabel(textOptions)?.let { currentLabels.add(it) }
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
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.pause()
    }
}
