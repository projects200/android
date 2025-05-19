package com.project200.feature.exercise

import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.project200.domain.model.BaseResult
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.FragmentExerciseDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class ExerciseDetailFragment: BindingFragment<FragmentExerciseDetailBinding>(R.layout.fragment_exercise_detail) {
    private val viewModel: ExerciseViewModel by viewModels()

    override fun getViewBinding(view: View): FragmentExerciseDetailBinding {
        return FragmentExerciseDetailBinding.bind(view)
    }

    override fun setupViews() {
        super.setupViews()
    }

    override fun setupObservers() {
        super.setupObservers()

        viewModel.exerciseRecord.observe(viewLifecycleOwner) { result ->
            when (result) {
                is BaseResult.Success -> {
                    Timber.tag(TAG).d("")
                }
                is BaseResult.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        const val TAG = "ExerciseDetailFragment"
    }
}