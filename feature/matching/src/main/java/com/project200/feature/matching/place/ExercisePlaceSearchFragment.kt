package com.project200.feature.matching.place

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.project200.common.constants.RuleConstants
import com.project200.domain.model.BaseResult
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.matching.R
import com.project200.undabang.feature.matching.databinding.FragmentExercisePlaceSearchBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import androidx.core.view.isVisible
import com.project200.domain.model.KakaoPlaceInfo

@AndroidEntryPoint
class ExercisePlaceSearchFragment : BindingFragment<FragmentExercisePlaceSearchBinding>(R.layout.fragment_exercise_place_search) {

    private var kakaoMap: KakaoMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val viewModel: ExercisePlaceSearchViewModel by viewModels()

    private lateinit var searchedPlaceAdapter: SearchedPlaceRVAdapter

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

    override fun setupViews() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        initMapView()
        initClickListeners()
        setupRecyclerView()
    }

    private fun initClickListeners() {
        binding.backBtn.setOnClickListener {
            // 검색 결과가 보이는 상태면 검색 결과 레이아웃을 숨기고, 그렇지 않으면 이전 화면으로 돌아감
            if(binding.searchedPlaceCl.isVisible) {
                binding.searchedPlaceCl.visibility = View.GONE
            } else {
                findNavController().popBackStack()
            }
        }

        binding.searchBtn.setOnClickListener {
            val query = binding.searchExercisePlaceEt.text.toString()
            if (query.isNotBlank()) {
                viewModel.searchPlacesByKeyword(
                    query = query,
                    latitude = kakaoMap?.cameraPosition?.position?.latitude
                        ?: RuleConstants.SEOUL_CITY_HALL_LATITUDE,
                    longitude = kakaoMap?.cameraPosition?.position?.longitude
                        ?: RuleConstants.SEOUL_CITY_HALL_LONGITUDE
                )
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.no_search_keyword),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.registerExercisePlaceBtn.setOnClickListener {
            val currentPlace = viewModel.place.value
            if(currentPlace == null) {
                Toast.makeText(requireContext(), R.string.error_cannot_find_address, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            findNavController().navigate(
                ExercisePlaceSearchFragmentDirections.actionExercisePlaceSearchFragmentToExercisePlaceRegisterFragment(
                    name = currentPlace.placeName,
                    address = currentPlace.address,
                    latitude = currentPlace.latitude.toString(),
                    longitude = currentPlace.longitude.toString()
                )
            )
        }
    }

    // 지도 초기화 함수
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
                    setupInitialMapPosition()
                    setMapListeners()
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
     * 지도 이동 종료 리스너 설정 함수
     * 지도 이동이 끝날 때마다 호출되어 마커의 끝부분에 맞춰 실제 지도 좌표의 주소를 가져옵니다.
     *
     * 마커의 끝부분에 해당하는 화면 상의 픽셀(px) 좌표를 계산합니다.
     * X 좌표: MapView의 정중앙
     * Y 좌표: 마커 이미지의 하단(bottom) - MapView의 상단(top)
     */
    private fun setMapListeners() {
        kakaoMap?.setOnCameraMoveEndListener { _, cameraPosition, _ ->
            val map = kakaoMap ?: return@setOnCameraMoveEndListener

            val targetScreenX = binding.mapView.width / 2
            val targetScreenY = binding.centerMarkerIv.bottom - binding.mapView.top

            val targetLatLng = map.fromScreenPoint(targetScreenX, targetScreenY)

            if (targetLatLng != null) {
                viewModel.onMapMoved(targetLatLng.latitude, targetLatLng.longitude)
            }
        }
    }

    override fun setupObservers() {
        viewModel.placeInfoResult.observe(viewLifecycleOwner) { result ->
            if (result is BaseResult.Success) {
                handlePlaceInfo(result.data)
            } else {
                // 주소 정보를 가져오지 못한 경우
                Toast.makeText(requireContext(), R.string.error_cannot_find_address, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.searchResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is BaseResult.Success -> {
                    binding.searchedPlaceCl.visibility = View.VISIBLE
                    if (result.data.isNotEmpty()) {
                        binding.searchedPlaceRv.visibility = View.VISIBLE
                        binding.searchedPlaceEmptyTv.visibility = View.GONE
                        searchedPlaceAdapter.submitList(result.data)
                    } else {
                        binding.searchedPlaceRv.visibility = View.GONE
                        binding.searchedPlaceEmptyTv.visibility = View.VISIBLE
                    }
                }
                is BaseResult.Error -> {
                    Toast.makeText(requireContext(), getString(R.string.error_failed_to_search_place), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handlePlaceInfo(place: KakaoPlaceInfo) {
        if (place.placeName.isEmpty() || place.placeName == place.address) { // 장소명이 없는 경우
            binding.placeAddressTv.visibility = View.GONE
            binding.placeNameTv.text = place.address
        } else { // 장소명이 있는 경우
            binding.placeAddressTv.visibility = View.VISIBLE
            binding.placeNameTv.text = place.placeName
            binding.placeAddressTv.text = place.address
        }
    }

    /**
     * 검색 결과 RecyclerView 설정 함수
     */
    private fun setupRecyclerView() {
        searchedPlaceAdapter = SearchedPlaceRVAdapter { place ->
            // 검색 결과 아이템 클릭 시 동작
            moveCamera(LatLng.from(place.latitude.toDouble(), place.longitude.toDouble()))
            binding.searchedPlaceCl.visibility = View.GONE
            handlePlaceInfo(place)
            viewModel.selectSearchedPlace(place)
        }
        binding.searchedPlaceRv.apply {
            adapter = searchedPlaceAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    /**
     * 현재 위치로 카메라를 이동시키는 함수
     */
    @SuppressLint("MissingPermission")
    private fun moveToCurrentLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                moveCamera(LatLng.from(location.latitude, location.longitude))
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