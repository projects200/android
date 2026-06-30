package com.project200.feature.matching.map

import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.project200.feature.matching.map.cluster.MapClusterItem
import com.project200.feature.matching.map.compose.MatchingMapScreen
import com.project200.feature.matching.map.filter.FilterBottomSheetDialog
import com.project200.feature.matching.map.filter.MatchingFilterRVAdapter
import com.project200.feature.matching.utils.MatchingFilterType
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.compose.applyAppTheme
import com.project200.undabang.feature.matching.R
import com.project200.undabang.feature.matching.databinding.FragmentMatchingMapBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MatchingMapFragment :
    BindingFragment<FragmentMatchingMapBinding>(R.layout.fragment_matching_map) {
    private val viewModel: MatchingMapViewModel by viewModels()

    private val filterAdapter by lazy {
        MatchingFilterRVAdapter(
            onFilterClick = { type -> viewModel.onFilterTypeClicked(type) },
            onClearClick = { viewModel.clearFilters() },
        )
    }

    override fun getViewBinding(view: View): FragmentMatchingMapBinding {
        return FragmentMatchingMapBinding.bind(view)
    }

    override fun setupViews() {
        binding.mapComposeView.applyAppTheme {
            MatchingMapScreen(
                viewModel = viewModel,
                onClusterClick = { items -> showMembersBottomSheet(items) },
                onPlaceMarkerClick = {
                    findNavController().navigate(
                        MatchingMapFragmentDirections.actionMatchingMapFragmentToExercisePlaceFragment(),
                    )
                },
            )
        }

        initListeners()
        binding.matchingFilterRv.adapter = filterAdapter
        filterAdapter.submitFilterList(MatchingFilterType.entries)
    }

    private fun initListeners() {
        binding.currentLocationBtn.setOnClickListener {
            // TODO: 현재 위치 이동 구현
        }

        binding.exercisePlaceListBtn.setOnClickListener {
            findNavController().navigate(
                MatchingMapFragmentDirections.actionMatchingMapFragmentToExercisePlaceFragment(),
            )
        }
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
                    viewModel.filterState.collect { state ->
                        filterAdapter.submitFilterState(state)
                    }
                }
                launch {
                    viewModel.currentFilterType.collect { type ->
                        showFilterBottomSheet(type)
                    }
                }
                launch {
                    viewModel.isFilterLoading.collect { isLoading ->
                        binding.filterLoadingGroup.isVisible = isLoading
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
