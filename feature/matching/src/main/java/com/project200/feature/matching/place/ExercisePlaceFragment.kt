package com.project200.feature.matching.place

import android.view.ContextThemeWrapper
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.project200.domain.model.ExercisePlace
import com.project200.feature.matching.utils.ExercisePlaceErrorType
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.utils.MenuStyler
import com.project200.undabang.feature.matching.R
import com.project200.undabang.feature.matching.databinding.FragmentExercisePlaceBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ExercisePlaceFragment : BindingFragment<FragmentExercisePlaceBinding> (R.layout.fragment_exercise_place) {
    private val viewModel: ExercisePlaceViewModel by viewModels()
    private lateinit var exercisePlaceAdapter: ExercisePlaceRVAdapter

    override fun getViewBinding(view: View): FragmentExercisePlaceBinding {
        return FragmentExercisePlaceBinding.bind(view)
    }

    override fun setupViews() {
        binding.baseToolbar.apply {
            setTitle(getString(R.string.exercise_place))
            showBackButton(true) { requireActivity().onBackPressedDispatcher.onBackPressed() }
        }
        binding.exercisePlaceSearchBtn.setOnClickListener {
            findNavController().navigate(
                ExercisePlaceFragmentDirections.actionExercisePlaceFragmentToExercisePlaceSearchFragment()
            )
        }

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        exercisePlaceAdapter =
            ExercisePlaceRVAdapter(onMenuClicked = { place, view ->
                showPopupMenu(place, view)
            })

        binding.placeRv.apply {
            adapter = exercisePlaceAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    override fun setupObservers() {
        viewModel.places.observe(viewLifecycleOwner) { places ->
            if (places.isEmpty()) {
                binding.emptyPlaceTv.visibility = View.VISIBLE
                binding.placeRv.visibility = View.GONE
            } else {
                binding.emptyPlaceTv.visibility = View.GONE
                binding.placeRv.visibility = View.VISIBLE
                exercisePlaceAdapter.submitList(places)
            }
        }

        viewModel.errorToast.observe(viewLifecycleOwner) {
            val message =
                when (it) {
                    ExercisePlaceErrorType.LOAD_FAILED -> R.string.error_failed_to_load_exercise_place
                    ExercisePlaceErrorType.DELETE_FAILED -> {
                        binding.emptyPlaceTv.visibility = View.VISIBLE
                        binding.placeRv.visibility = View.GONE
                        R.string.error_failed_to_delete_exercise_place
                    }
                }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPopupMenu(
        place: ExercisePlace,
        view: View,
    ) {
        val contextWrapper = ContextThemeWrapper(requireContext(), com.project200.undabang.presentation.R.style.PopupItemStyle)

        PopupMenu(contextWrapper, view).apply {
            menuInflater.inflate(R.menu.exercise_place_item_menu, this.menu)

            menu.findItem(R.id.action_edit)?.let {
                MenuStyler.applyTextColor(requireContext(), it, android.R.color.black)
            }
            menu.findItem(R.id.action_delete)?.let {
                MenuStyler.applyTextColor(requireContext(), it, com.project200.undabang.presentation.R.color.error_led)
            }

            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit -> {
                        findNavController().navigate(
                            ExercisePlaceFragmentDirections.actionExercisePlaceFragmentToExercisePlaceRegisterFragment(
                                placeId = place.id,
                                name = place.name,
                                address = place.address,
                                latitude = place.latitude.toString(),
                                longitude = place.longitude.toString(),
                            ),
                        )
                    }
                    R.id.action_delete -> {
                        viewModel.deleteExercisePlace(place.id)
                    }
                }
                true
            }

            MenuStyler.showIcons(this)
        }.show()
    }
}
