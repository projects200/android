package com.project200.feature.exercise.list

import android.content.Context
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.project200.common.utils.CommonDateTimeFormatters.YYYY_MM_DD_KOR
import com.project200.common.utils.CommonDateTimeFormatters.YY_MM_DD_HH_MM
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.base.DatePickerDialogFragment
import com.project200.presentation.navigator.FragmentNavigator
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.FragmentExerciseListBinding
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDateTime
import java.util.Date

@AndroidEntryPoint
class ExerciseListFragment: BindingFragment<FragmentExerciseListBinding>(R.layout.fragment_exercise_list) {
    private val viewModel: ExerciseListViewModel by viewModels()
    private var fragmentNavigator: FragmentNavigator? = null
    private lateinit var exerciseAdapter: ExerciseListAdapter

    override fun getViewBinding(view: View): FragmentExerciseListBinding {
        return FragmentExerciseListBinding.bind(view)
    }

    override fun setupViews() {
        with(binding) {
            baseToolbar.apply {
                setTitle(getString(R.string.exercise_record))
                showBackButton(true) { findNavController().navigateUp() }
                setSubButton(R.drawable.ic_setting) { fragmentNavigator?.navigateFromExerciseListToSetting() }
            }

            exerciseListDateTv.setOnClickListener {
                showDatePickerDialog()
            }

            exerciseCreateBtn.setOnClickListener {
                fragmentNavigator?.navigateFromExerciseListToExerciseForm(CREATE_RECORD_ID)
            }
            setupRecyclerView()
        }
    }

    private fun setupRecyclerView() {
        exerciseAdapter = ExerciseListAdapter { recordId ->
            fragmentNavigator?.navigateFromExerciseListToExerciseForm(recordId)
        }

        binding.exerciseListRv.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = exerciseAdapter
        }
    }

    override fun setupObservers() {
        viewModel.exerciseList.observe(viewLifecycleOwner) { list ->
            exerciseAdapter.submitList(list)
            binding.emptyView.isVisible = list.isNullOrEmpty()
        }

        viewModel.currentDate.observe(viewLifecycleOwner) { monthText ->
            binding.exerciseListDateTv.text = monthText.format(YYYY_MM_DD_KOR)
        }
    }

    private fun showDatePickerDialog() {
        val currentDateString = viewModel.currentDate.value?.toString()

        DatePickerDialogFragment(currentDateString) { selectedDateString ->
            viewModel.changeDate(selectedDateString)
        }.show(parentFragmentManager, DatePickerDialogFragment::class.java.simpleName)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is FragmentNavigator) {
            fragmentNavigator = context
        } else {
            throw ClassCastException("$context must implement FragmentNavigator")
        }
    }

    override fun onDetach() {
        fragmentNavigator = null
        super.onDetach()
    }

    companion object {
        const val CREATE_RECORD_ID = -1L
    }
}