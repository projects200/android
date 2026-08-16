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
import com.kakao.vectormap.LatLng
import com.project200.common.constants.RuleConstants.SEOUL_CITY_HALL_LATITUDE
import com.project200.common.constants.RuleConstants.SEOUL_CITY_HALL_LONGITUDE
import com.project200.common.constants.RuleConstants.ZOOM_LEVEL
import com.project200.feature.matching.map.cluster.MapClusterItem
import com.project200.feature.matching.map.compose.MatchingMapController
import com.project200.feature.matching.map.compose.MatchingMapScreen
import com.project200.feature.matching.map.filter.FilterBottomSheetDialog
import com.project200.feature.matching.utils.MatchingFilterType
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.compose.applyAppTheme
import com.project200.undabang.feature.matching.R
import com.project200.undabang.feature.matching.databinding.FragmentMatchingMapBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MatchingMapFragment :
    BindingFragment<FragmentMatchingMapBinding>(R.layout.fragment_matching_map) {
    private val viewModel: MatchingMapViewModel by viewModels()

    // 지도 본체(MatchingMapScreen) 내부 카메라를 제어하기 위한 통로
    private val mapController = MatchingMapController()
    private var isMapInitialized = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) moveToCurrentLocation()
        }

    override fun getViewBinding(view: View): FragmentMatchingMapBinding {
        return FragmentMatchingMapBinding.bind(view)
    }

    override fun setupViews() {
        isMapInitialized = false
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // 지도·툴바(필터)·현재위치·로딩까지 전체를 Compose(MatchingMapScreen)로 호스팅.
        binding.mapComposeView.applyAppTheme {
            MatchingMapScreen(
                viewModel = viewModel,
                controller = mapController,
                onMapReady = { restoreInitialCamera() },
                onClusterClick = { items -> showMembersBottomSheet(items) },
                onPlaceMarkerClick = { navigateToExercisePlace() },
                onCurrentLocationClick = { checkPermissionAndMove() },
                onFilterClick = { type -> viewModel.onFilterTypeClicked(type) },
                onClearClick = { viewModel.clearFilters() },
                onExercisePlaceListClick = { navigateToExercisePlace() },
            )
        }
    }

    private fun navigateToExercisePlace() {
        findNavController().navigate(
            MatchingMapFragmentDirections.actionMatchingMapFragmentToExercisePlaceFragment(),
        )
    }

    /**
     * 지도가 준비되면 1회 호출되어 초기 카메라 위치를 복원한다.
     * 저장된 위치가 있으면 그곳으로, 없으면 현재 위치(권한 시) 또는 기본 위치(서울시청)로 이동한다.
     */
    private fun restoreInitialCamera() {
        if (isMapInitialized) return

        val savedPosition = viewModel.initialMapPosition.value
        if (isLocationPermissionGranted()) {
            if (savedPosition != null) {
                mapController.moveCamera(
                    LatLng.from(savedPosition.latitude, savedPosition.longitude),
                    savedPosition.zoomLevel,
                )
            } else {
                moveToCurrentLocation()
            }
        } else {
            mapController.moveCamera(
                LatLng.from(SEOUL_CITY_HALL_LATITUDE, SEOUL_CITY_HALL_LONGITUDE),
                ZOOM_LEVEL,
            )
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        isMapInitialized = true
    }

    private fun checkPermissionAndMove() {
        if (isLocationPermissionGranted()) {
            moveToCurrentLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission") // 권한은 isLocationPermissionGranted()로 이미 확인됨
    private fun moveToCurrentLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    mapController.moveCamera(
                        LatLng.from(location.latitude, location.longitude),
                        ZOOM_LEVEL,
                    )
                } else {
                    fallbackToDefaultLocation(cause = null)
                }
            }
            .addOnFailureListener { e ->
                fallbackToDefaultLocation(cause = e)
            }
    }

    private fun fallbackToDefaultLocation(cause: Throwable?) {
        cause?.let { Timber.e(it, "getLastLocation failed") }
        Toast.makeText(
            requireContext(),
            R.string.error_cannot_find_current_location,
            Toast.LENGTH_SHORT,
        ).show()
        mapController.moveCamera(
            LatLng.from(SEOUL_CITY_HALL_LATITUDE, SEOUL_CITY_HALL_LONGITUDE),
            ZOOM_LEVEL,
        )
    }

    private fun isLocationPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
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
                launch {
                    viewModel.currentFilterType.collect { type ->
                        showFilterBottomSheet(type)
                    }
                }
                launch {
                    viewModel.zoomLevelWarning.collect {
                        Toast.makeText(
                            requireContext(),
                            R.string.zoom_level_warning,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
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
        dialog.isCancelable = false
        dialog.show(parentFragmentManager, this::class.java.simpleName)
    }

    private fun showMembersBottomSheet(items: List<MapClusterItem>) {
        val bottomSheet =
            MembersBottomSheetDialog(items) { item ->
                findNavController().navigate(
                    MatchingMapFragmentDirections.actionMatchingMapFragmentToMatchingProfileFragment(
                        memberId = item.member.memberId,
                        placeId = item.location.placeId,
                    ),
                )
            }
        bottomSheet.show(parentFragmentManager, MembersBottomSheetDialog::class.java.simpleName)
    }

    private fun showFilterBottomSheet(type: MatchingFilterType) {
        val bottomSheet =
            FilterBottomSheetDialog(
                filterType = type,
                onOptionSelected = { selectedDomainData ->
                    viewModel.onFilterOptionSelected(type, selectedDomainData)
                },
            )
        bottomSheet.show(childFragmentManager, FilterBottomSheetDialog::class.java.simpleName)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshExercisePlaces()
    }
}
