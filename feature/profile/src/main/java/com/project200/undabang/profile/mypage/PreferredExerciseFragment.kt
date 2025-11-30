package com.project200.undabang.profile.mypage

import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.FragmentPreferredExerciseBinding
import com.project200.undabang.profile.utils.CompletionState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PreferredExerciseFragment :
    BindingFragment<FragmentPreferredExerciseBinding>(R.layout.fragment_preferred_exercise) {

    private val viewModel: PreferredExerciseViewModel by viewModels()

    private val args: PreferredExerciseFragmentArgs by navArgs()

    override fun getViewBinding(view: View): FragmentPreferredExerciseBinding {
        return FragmentPreferredExerciseBinding.bind(view)
    }

    override fun setupViews() {
        binding.baseToolbar.setTitle(getString(R.string.preferred_exercise))
        initClickListener()
        viewModel.initNickname(args.nickname)
        if (childFragmentManager.findFragmentById(R.id.preferred_exercise_container) == null) {
            replaceFragment(PreferredExerciseTypeFragment())
        }

        childFragmentManager.addOnBackStackChangedListener {
            updateCompleteButtonState()
        }
    }

    override fun setupObservers() {
        viewModel.completionState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is CompletionState.Loading -> {
                    binding.completeBtn.isEnabled = false
                }
                is CompletionState.Success -> {
                    Toast.makeText(requireContext(), R.string.preferred_exercise_success, Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                    viewModel.consumeCompletionState()
                }
                is CompletionState.NoChanges -> {
                    Toast.makeText(requireContext(), R.string.preferred_exercise_no_changed, Toast.LENGTH_SHORT).show()
                    binding.completeBtn.isEnabled = true
                    viewModel.consumeCompletionState()
                }
                is CompletionState.NoneSelected -> {
                    Toast.makeText(requireContext(), R.string.preferred_exercise_none_selected, Toast.LENGTH_SHORT).show()
                    binding.completeBtn.isEnabled = true
                    viewModel.consumeCompletionState()
                }
                is CompletionState.Error -> {
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    binding.completeBtn.isEnabled = true
                    viewModel.consumeCompletionState()
                }
                is CompletionState.Idle -> {
                    binding.completeBtn.isEnabled = true
                }
            }
        }
    }
    private fun initClickListener() {
        binding.baseToolbar.showBackButton(true, onClick = {
            if (childFragmentManager.backStackEntryCount > 0) {
                childFragmentManager.popBackStack()
            } else {
                findNavController().popBackStack()
            }
        })

        binding.completeBtn.setOnClickListener {
            when (childFragmentManager.findFragmentById(R.id.preferred_exercise_container)) {
                is PreferredExerciseTypeFragment -> {
                    replaceFragment(PreferredExerciseDetailFragment(), addToBackStack = true)
                }
                is PreferredExerciseDetailFragment -> {
                    viewModel.completePreferredExerciseChanges()
                }
            }
        }
    }

    private fun updateCompleteButtonState() {
        when (childFragmentManager.findFragmentById(R.id.preferred_exercise_container)) {
            is PreferredExerciseTypeFragment -> {
                binding.completeBtn.text = getString(com.project200.undabang.presentation.R.string.next)
            }
            is PreferredExerciseDetailFragment -> {
                binding.completeBtn.text = getString(com.project200.undabang.presentation.R.string.complete)
            }
        }
    }

    /**
     * FrameLayout의 프래그먼트를 교체하는 함수
     * @param fragment 교체할 프래그먼트
     * @param addToBackStack 뒤로가기 스택에 추가할지 여부
     */
    private fun replaceFragment(fragment: Fragment, addToBackStack: Boolean = false) {
        val transaction = childFragmentManager.beginTransaction()
            .replace(R.id.preferred_exercise_container, fragment)

        if (addToBackStack) {
            transaction.addToBackStack(null)
        }

        transaction.commit()
    }
}