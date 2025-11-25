package com.project200.undabang.profile.mypage

import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.FragmentPreferredExerciseBinding
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
        binding.baseToolbar.apply {
            setTitle(getString(R.string.preferred_exercise))
            showBackButton(true, onClick = { findNavController().popBackStack() })
        }
        viewModel.initNickname(args.nickname)
        if (childFragmentManager.findFragmentById(R.id.preferred_exercise_container) == null) {
            replaceFragment(PreferredExerciseTypeFragment())
        }
        initClickListener()
    }

    private fun initClickListener() {
        binding.completeBtn.setOnClickListener {
            when (childFragmentManager.findFragmentById(R.id.preferred_exercise_container)) {
                is PreferredExerciseTypeFragment -> {
                    // TODO: 현재 프래그먼트가 종류 선택이면 상세 설정으로 이동
                }
                // TODO: 상세 설정이면 완료 api 호출
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