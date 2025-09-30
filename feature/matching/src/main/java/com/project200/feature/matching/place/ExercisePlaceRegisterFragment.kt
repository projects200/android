package com.project200.feature.matching.place

import android.view.View
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.project200.domain.model.BaseResult
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.matching.R
import com.project200.undabang.feature.matching.databinding.FragmentExercisePlaceRegisterBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint // Hilt 어노테이션 추가
class ExercisePlaceRegisterFragment : BindingFragment<FragmentExercisePlaceRegisterBinding> (R.layout.fragment_exercise_place_register) {
    private val viewModel: ExercisePlaceRegisterViewModel by viewModels()
    private val args: ExercisePlaceRegisterFragmentArgs by navArgs()

    override fun getViewBinding(view: View): FragmentExercisePlaceRegisterBinding {
        return FragmentExercisePlaceRegisterBinding.bind(view)
    }

    override fun setupViews() {
        binding.baseToolbar.apply {
            setTitle(getString(R.string.exercise_place_register_title))
            showBackButton(true) { findNavController().navigateUp() }
        }
        binding.placeAddressTv.text = args.address

        viewModel.initializePlaceInfo(
            id = args.placeId,
            placeName = args.name,
            placeAddress = args.address,
            latitude = args.latitude.toDouble(),
            longitude = args.longitude.toDouble(),
        )
        binding.placeNameEt.setText(args.name)
        setupListeners()
    }

    private fun setupListeners() {
        binding.placeNameEt.doAfterTextChanged { text ->
            viewModel.onPlaceNameChanged(text.toString())
        }

        binding.registerBtn.setOnClickListener {
            if (binding.placeNameEt.text.toString().isEmpty()) {
                Toast.makeText(requireContext(), R.string.empty_place_name, Toast.LENGTH_SHORT).show()
            }
            viewModel.confirmExercisePlace()
        }
    }

    override fun setupObservers() {
        viewModel.customPlaceName.observe(viewLifecycleOwner) { name ->
            binding.placeNameTv.text = if (name.isBlank()) getString(R.string.place_name_hint) else name
        }

        viewModel.registrationResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is BaseResult.Success -> {
                    Toast.makeText(requireContext(), getString(R.string.success_register_place), Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_complete_registration_and_go_to_place_list)
                }
                is BaseResult.Error -> {
                    Toast.makeText(requireContext(), R.string.error_fail_to_register_place, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.editResult.observe(viewLifecycleOwner) { result ->
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
