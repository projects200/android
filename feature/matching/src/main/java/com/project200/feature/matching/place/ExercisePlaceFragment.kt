package com.project200.feature.matching.place

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.project200.feature.matching.MatchingFixtureEvidence
import com.project200.feature.matching.utils.ExercisePlaceErrorType
import com.project200.presentation.compose.applyAppTheme
import com.project200.undabang.feature.matching.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ExercisePlaceFragment : Fragment() {
    private val viewModel: ExercisePlaceViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                val places by viewModel.places.collectAsStateWithLifecycle()
                val loadState by viewModel.loadState.collectAsStateWithLifecycle()
                // 빈 초기 목록을 조회 성공으로 오인하지 않고, 실제 서버 조회/삭제 완료만 기록합니다.
                LaunchedEffect(places, loadState) {
                    if (loadState == ExercisePlaceLoadState.READY) {
                        MatchingFixtureEvidence.places(requireContext(), places)
                    }
                }
                ExercisePlaceScreen(
                    places = places,
                    loadState = loadState,
                    onBackClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                    onAddClick = {
                        if (places.size >= MAX_PLACE_COUNT) {
                            Toast.makeText(requireContext(), R.string.max_place_count, Toast.LENGTH_SHORT).show()
                        } else {
                            findNavController().navigate(
                                ExercisePlaceFragmentDirections.actionExercisePlaceFragmentToExercisePlaceSearchFragment(),
                            )
                        }
                    },
                    onEditClick = { place ->
                        findNavController().navigate(
                            ExercisePlaceFragmentDirections.actionExercisePlaceFragmentToExercisePlaceRegisterFragment(
                                placeId = place.id,
                                name = place.name,
                                address = place.address,
                                latitude = place.latitude.toString(),
                                longitude = place.longitude.toString(),
                            ),
                        )
                    },
                    onDeleteClick = { place -> viewModel.deleteExercisePlace(place.id) },
                )
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errorToast.collect { type ->
                    val message =
                        when (type) {
                            ExercisePlaceErrorType.LOAD_FAILED -> R.string.error_failed_to_load_exercise_place
                            ExercisePlaceErrorType.DELETE_FAILED -> R.string.error_failed_to_delete_exercise_place
                        }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.getExercisePlaces()
    }

    companion object {
        private const val MAX_PLACE_COUNT = 10
    }
}
