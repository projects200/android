package com.project200.undabang.profile.mypage.preferredExercise

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.utils.UiUtils.dpToPx
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.FragmentPreferredExerciseTypeBinding
import com.project200.undabang.profile.utils.FlexboxItemDecoration
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PreferredExerciseTypeFragment :
    BindingFragment<FragmentPreferredExerciseTypeBinding>(R.layout.fragment_preferred_exercise_type) {
    private val viewModel: PreferredExerciseViewModel by viewModels({ requireParentFragment() })

    private val kindsRvAdapter =
        PreferredExerciseTypeRVAdapter(
            onItemClicked = { exercise ->
                viewModel.updateSelectedExercise(exercise)
            },
            onLimitReached = {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.preferred_exercise_type_max_error),
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )

    override fun getViewBinding(view: View): FragmentPreferredExerciseTypeBinding {
        return FragmentPreferredExerciseTypeBinding.bind(view)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        setupObservers()
    }

    override fun setupViews() {
        binding.typeTitleTv.text = getString(R.string.preferred_exercise_type_title, viewModel.nickname)
        setupRecyclerView()
    }

    override fun setupObservers() {
        viewModel.exerciseUiModels.observe(viewLifecycleOwner) { uiModels ->
            kindsRvAdapter.submitList(uiModels)
        }
    }

    private fun setupRecyclerView() {
        val flexboxLayoutManager =
            FlexboxLayoutManager(requireContext()).apply {
                flexDirection = FlexDirection.ROW
                flexWrap = FlexWrap.WRAP
                justifyContent = JustifyContent.FLEX_START
            }

        binding.exerciseTypeRv.apply {
            layoutManager = flexboxLayoutManager
            adapter = kindsRvAdapter
            if (itemDecorationCount == 0) {
                addItemDecoration(FlexboxItemDecoration(dpToPx(requireContext(), 10f)))
            }
        }
    }
}
