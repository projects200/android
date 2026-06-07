package com.project200.feature.matching.place

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.project200.domain.model.BaseResult
import com.project200.presentation.compose.applyAppTheme
import com.project200.undabang.feature.matching.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ExercisePlaceRegisterFragment : Fragment() {
    private val viewModel: ExercisePlaceRegisterViewModel by viewModels()
    private val args: ExercisePlaceRegisterFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.initializePlaceInfo(
            id = args.placeId,
            placeName = args.name,
            placeAddress = args.address,
            latitude = args.latitude.toDouble(),
            longitude = args.longitude.toDouble(),
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                val placeNameInput by viewModel.customPlaceName.collectAsStateWithLifecycle()
                val placeAddress by viewModel.placeAddress.collectAsStateWithLifecycle()
                ExercisePlaceRegisterScreen(
                    placeName = if (placeNameInput.isBlank()) getString(R.string.place_name_hint) else placeNameInput,
                    placeAddress = placeAddress,
                    placeNameInput = placeNameInput,
                    onPlaceNameChange = viewModel::onPlaceNameChanged,
                    onRegisterClick = {
                        if (placeNameInput.isBlank()) {
                            Toast.makeText(requireContext(), R.string.empty_place_name, Toast.LENGTH_SHORT).show()
                        }
                        viewModel.confirmExercisePlace()
                    },
                    onBackClick = { findNavController().navigateUp() },
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
                launch {
                    viewModel.registrationResult.collect { result ->
                        when (result) {
                            is BaseResult.Success -> {
                                Toast.makeText(requireContext(), getString(R.string.success_register_place), Toast.LENGTH_SHORT).show()
                                findNavController().navigate(R.id.action_complete_registration_and_go_to_place_list)
                            }
                            is BaseResult.Error -> {
                                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                launch {
                    viewModel.editResult.collect { result ->
                        when (result) {
                            is BaseResult.Success -> {
                                Toast.makeText(requireContext(), getString(R.string.success_edit_place), Toast.LENGTH_SHORT).show()
                                findNavController().navigate(R.id.action_complete_registration_and_go_to_place_list)
                            }
                            is BaseResult.Error -> {
                                Toast.makeText(requireContext(), R.string.error_fail_to_register_place, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }
}
