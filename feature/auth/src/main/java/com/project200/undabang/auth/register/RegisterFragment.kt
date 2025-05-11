package com.project200.undabang.auth.register

import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.utils.DatePickerDialogFragment
import com.project200.undabang.feature.auth.R
import com.project200.undabang.feature.auth.databinding.FragmentRegisterBinding

class RegisterFragment : BindingFragment<FragmentRegisterBinding>(R.layout.fragment_register) {

    private val viewModel: RegisterViewModel by viewModels()

    override fun getViewBinding(view: View): FragmentRegisterBinding {
        return FragmentRegisterBinding.bind(view)
    }

    override fun setupViews() = with(binding) {
        nicknameEt.doAfterTextChanged {
            viewModel.updateNickname(it.toString())
        }

        birthDayTv.setOnClickListener {
            DatePickerDialogFragment { date ->
                viewModel.updateBirth(date)
            }.show(parentFragmentManager, DatePickerDialogFragment::class.java.simpleName)
        }

        maleBtnImv.setOnClickListener {
            viewModel.selectGender(MALE)
        }

        femaleBtnImv.setOnClickListener {
            viewModel.selectGender(FEMALE)
        }
    }

    override fun setupObservers() = with(viewModel) {
        nickname.observe(viewLifecycleOwner) {

        }

        birth.observe(viewLifecycleOwner) { birthDay ->
            binding.birthDayTv.text = birthDay ?: getString(R.string.birth_default)
        }

        gender.observe(viewLifecycleOwner) { gender ->
            binding.maleBtnImv.isSelected = gender == MALE
            binding.femaleBtnImv.isSelected = gender == FEMALE
        }

        isFormValid.observe(viewLifecycleOwner) { isValid ->
            binding.registerCompleteBtn.isEnabled = isValid
        }
    }

    companion object {
        const val MALE = "male"
        const val FEMALE = "female"
    }
}
