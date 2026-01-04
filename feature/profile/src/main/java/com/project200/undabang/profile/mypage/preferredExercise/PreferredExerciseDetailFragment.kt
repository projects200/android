package com.project200.undabang.profile.mypage.preferredExercise

import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.FragmentPreferredExerciseDetailBinding
import com.project200.common.utils.PreferredExerciseDayFormatter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PreferredExerciseDetailFragment : BindingFragment<FragmentPreferredExerciseDetailBinding>(R.layout.fragment_preferred_exercise_detail) {
    private val viewModel: PreferredExerciseViewModel by viewModels({ requireParentFragment() })
    private lateinit var detailAdapter: PreferredExerciseDetailRVAdapter

    @Inject
    lateinit var formatter: PreferredExerciseDayFormatter

    override fun getViewBinding(view: View): FragmentPreferredExerciseDetailBinding {
        return FragmentPreferredExerciseDetailBinding.bind(view)
    }

    override fun setupViews() {
        detailAdapter = PreferredExerciseDetailRVAdapter(viewModel, formatter)
        binding.exerciseRv.apply {
            adapter = detailAdapter
            layoutManager = LinearLayoutManager(requireContext())
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        }
    }

    override fun setupObservers() {
        viewModel.selectedExerciseUiModels.observe(viewLifecycleOwner) { uiModels ->
            detailAdapter.submitList(uiModels)
        }
    }
}